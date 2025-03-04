/******************************************************************************
 * Copyright (C) 2009 Low Heng Sin                                            *
 * Copyright (C) 2009 Idalica Corporation                                     *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program; if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 *****************************************************************************/
package org.compiere.apps.form;

import org.adempiere.core.domains.models.I_C_Invoice;
import org.adempiere.core.domains.models.I_C_Payment;
import org.adempiere.exceptions.AdempiereException;
import org.compiere.minigrid.IDColumn;
import org.compiere.minigrid.IMiniTable;
import org.compiere.model.*;
import org.compiere.process.DocAction;
import org.compiere.process.ProcessInfo;
import org.compiere.util.*;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Vector;
import java.util.logging.Level;

/**
 * 
 * @author Michael McKay, 
 * 				<li>ADEMPIERE-72 VLookup and Info Window improvements
 * 					https://adempiere.atlassian.net/browse/ADEMPIERE-72
 * @author Yamel Senih, ysenih@erpcya.com, ERPCyA http://www.erpcya.com
 *		<a href="https://github.com/adempiere/adempiere/issues/407">
 * 		@see FR [ 407 ] Enhance visualization of allocation payment window</a>
 */
public class Allocation
{
	public DecimalFormat format = DisplayType.getNumberFormat(DisplayType.Amount);

	/**	Logger			*/
	public static CLogger log = CLogger.getCLogger(Allocation.class);

	private boolean     calculating = false;
	public int         	currencyId = 0;
	public int         	bPartnerId = 0;
	public int 			projectId = 0;//Openup. Nicolas Sarlabos. 01/07/2019. #12307.
	public int 			orderId = 0;//Openup. Nicolas Sarlabos. 03/12/2019. #12922.
	public int          chargeId = 0;
	public int         	orgWriteId = 0;
	public String		description = null;
	private boolean		isSOTrx = false;
	private int         noInvoices = 0;
	private int         noPayments = 0;
	public BigDecimal	totalInv = new BigDecimal(0.0);
	public BigDecimal 	totalPay = new BigDecimal(0.0);
	public BigDecimal	totalDiff = new BigDecimal(0.0);
	
	public Timestamp 	allocDate = null;

	//  Index	changed if multi-currency
	private int         paymentPaidIndex = 10;
	private int         paymentOpenIndex = 9;
	//
	private int         invoiceOpenIndex = 6;
	private int         invoiceDiscountIndex = 7;
	private int         invoiceWriteOffIndex = 8; 
	private int         invoiceAppliedIndex = 9;
	private int 		invoiceOverUnderIndex = 10;
	
	public int         	orgId = 0;
	public String		apar = null;
	//	Window No
	private int			windowNo = 0;
	/**	From PO				*/
	private PO			poFrom = null;
	
	/**	Receivables & Payables	*/
	public static final String 	APAR_A = "A";
	/**	Payables only			*/
	public static final String 	APAR_P = "P";
	/**	Receivables only		*/
	public static final String 	APAR_R = "R";
	private ArrayList<Integer>	bpartnerCheck = new ArrayList<Integer>();
	
	/**
	 *
	 * @throws Exception
	 */
	public void dynInit() throws Exception {
		setFromPO(null);
		//
		log.info("Currency=" + currencyId);
	}
	
	/**
	 * Set from PO
	 * @param processInfo
	 */
	public void setFromPO(ProcessInfo processInfo) {
		if(processInfo != null
				&& processInfo.getTable_ID() > 0
				&& processInfo.getRecord_ID() > 0) {
			poFrom = MTable.get(Env.getCtx(), processInfo.getTable_ID())
				.getPO(processInfo.getRecord_ID(), null);
			//	
			bPartnerId = getDefaultBPartnerId();
			orgId = getDefaultOrgId();
			currencyId = getDefaultCurrencyId();
			//	
			isSOTrx = isDefaultSOTrx();
			projectId = getDefaultProjectId();//Openup. Nicolas Sarlabos. 01/07/2019. #12307.
		} else {
			bPartnerId = -1;
			orgId = Env.getAD_Org_ID(Env.getCtx());
			currencyId = Env.getContextAsInt(Env.getCtx(), "$C_Currency_ID");   //  default
		}
		//	
		Env.setContext(Env.getCtx(), getWindowNo(), "IsSOTrx", isSOTrx? "Y": "N");   //  defaults to no
	}
	
	/**
	 * Get Default BPartner
	 * @return
	 */
	private int getDefaultBPartnerId() {
		if(poFrom == null) {
			return -1;
		}
		//	
		return poFrom.get_ValueAsInt(I_C_Payment.COLUMNNAME_C_BPartner_ID);
	}

	/**
	 * Get Default BPartner
	 * @return
	 */
	private int getDefaultProjectId() {//Openup. Nicolas Sarlabos. 01/07/2019. #12307.
		if(poFrom == null) {
			return -1;
		}
		//
		return poFrom.get_ValueAsInt("C_Project_ID");
	}//Fin #12307.

	/**
	 * Get Default Org
	 * @return
	 */
	private int getDefaultOrgId() {
		if(poFrom == null) {
			return -1;
		}
		//	
		return poFrom.get_ValueAsInt(I_C_Payment.COLUMNNAME_AD_Org_ID);
	}
	
	/**
	 * Get Default Currency
	 * @return
	 */
	private int getDefaultCurrencyId() {
		if(poFrom == null) {
			return -1;
		}
		//	
		return poFrom.get_ValueAsInt(I_C_Payment.COLUMNNAME_C_Currency_ID);
	}
	
	/**
	 * Is Sales Trx
	 * @return
	 */
	private boolean isDefaultSOTrx() {
		if(poFrom == null) {
			return false;
		}
		//	
		int index = poFrom.get_ColumnIndex(I_C_Payment.COLUMNNAME_IsReceipt);
		if(index > 0) {
			return poFrom.get_ValueAsBoolean(I_C_Payment.COLUMNNAME_IsReceipt);
		} else {
			index = poFrom.get_ColumnIndex(I_C_Invoice.COLUMNNAME_IsSOTrx);
		}
		//	get
		if(index > 0) {
			return poFrom.get_ValueAsBoolean(I_C_Invoice.COLUMNNAME_IsSOTrx);
		}
		//	Default
		return false;
	}
	
	/**
	 * Is Default Multi currency
	 * @return
	 */
	public boolean isDefaultMultiCurrency() {
		int defaultCurrencyId = getDefaultCurrencyId();
		if(defaultCurrencyId > 0) {
			int currencyId = Env.getContextAsInt(Env.getCtx(), "$C_Currency_ID");
			return currencyId != defaultCurrencyId;
		}
		//	Default Return
		return false;
	}
	
	/**
	 * Is generated from parent
	 * @return
	 */
	public boolean isFromParent() {
		return poFrom != null;
	}
	
	/**
	 * Set Window no
	 * @param windowNo
	 */
	public void setWindowNo(int windowNo) {
		this.windowNo = windowNo;
	}
	
	/**
	 * Get Window No
	 * @return
	 */
	public int getWindowNo() {
		return windowNo;
	}
	
	/**
	 * Set Org to Context
	 */
	public void setAD_Org_ID() {
		if (orgId > 0)
			Env.setContext(Env.getCtx(), getWindowNo(), "AD_Org_ID", orgId);
		else
			Env.setContext(Env.getCtx(), getWindowNo(), "AD_Org_ID", "");
	}
	
	/**
	 * Get organization
	 * @return
	 */
	public int getAD_Org_ID() {
		return orgId;
	}
	
	/**
	 * Set Organization
	 * @param orgId
	 */
	public void setAD_Org_ID(int orgId) {
		this.orgId = orgId;
	}
	
	/**
	 * Ser DEfault Records
	 * @param payment
	 * @param invoice
	 */
	public void setDefaultRecord(IMiniTable payment, IMiniTable invoice) {
		if(!isFromParent()) {
			return;
		}
		//	
		if(poFrom.get_Table_ID() == I_C_Payment.Table_ID) {
			for(int row = 0; row < payment.getRowCount(); row++) {
				int paymentId = payment.getRowKey(row);
				if(paymentId == poFrom.get_ID()) {
					payment.setRowChecked(row, true);
				}
			}
		} else if(poFrom.get_Table_ID() == I_C_Invoice.Table_ID) {
			for(int row = 0; row < invoice.getRowCount(); row++) {
				int invoiceId = invoice.getRowKey(row);
				if(invoiceId == poFrom.get_ID()) {
					invoice.setRowChecked(row, true);
				}
			}
		}
	}
	
	/**
	 *  Load Business Partner Info
	 *  - Payments
	 *  - Invoices
	 */
	public void checkBPartner()
	{		
		log.config("BPartner=" + bPartnerId + ", Cur=" + currencyId);
		//  Need to have both values
		if (bPartnerId == 0 || currencyId == 0)
			return;

		//	Async BPartner Test
		Integer key = new Integer(bPartnerId);
		if (!bpartnerCheck.contains(key))
		{
			new Thread()
			{
				public void run()
				{
					MPayment.setIsAllocated (Env.getCtx(), bPartnerId, null);
					MInvoice.setIsPaid (Env.getCtx(), bPartnerId, null);
				}
			}.start();
			bpartnerCheck.add(key);
		}
	}
	
	/**
	 * Get Payment data
	 * @param isMultiCurrency
	 * @param date
	 * @param paymentTable
	 * @return
	 */
	public Vector<Vector<Object>> getPaymentData(boolean isMultiCurrency, Object date, IMiniTable paymentTable, BigDecimal currencyRate)
	{		
		/********************************
		 *  Load unallocated Payments
		 *      1-TrxDate, 2-DocumentNo, (3-Currency, 4-PayAmt,)
		 *      5-ConvAmt, 6-ConvOpen, 7-Allocated
		 */

		if(currencyRate == null)
			currencyRate = Env.ZERO;

		Vector<Vector<Object>> data = new Vector<Vector<Object>>();
		StringBuffer sql = new StringBuffer("SELECT p.DateTrx,p.DocumentNo,p.C_Payment_ID,"  //  1..3
				+ "c.ISO_Code,p.PayAmt,"                            //  4..5
				+ "currencyConvert_receipt(p.PayAmt,?,p.C_Currency_ID,?,p.AD_Client_ID,p.AD_Org_ID) AS ConvertedAmt,"//  6   #1, #2
				+ "currencyConvert_receipt(paymentAvailable(C_Payment_ID),?,p.C_Currency_ID,?,p.AD_Client_ID,p.AD_Org_ID) AS AvailableAmt,"  //  7   #3, #4
				+ "p.MultiplierAP, p.IsReceipt, p.AD_Org_ID, p.Description, p.c_doctype_id, coalesce(p.c_project_id, 0) as c_project_id " // 8..11 //Openup. Nicolas Sarlabos. 01/07/2019. #12307.
				+ "FROM C_Payment_v p"		//	Corrected for AP/AR
				+ " INNER JOIN C_Currency c ON (p.C_Currency_ID=c.C_Currency_ID) "
				+ "WHERE p.IsAllocated='N' AND p.Processed='Y'"
				+ " AND p.C_Charge_ID IS NULL"		//	Prepayments OK
				+ " AND p.C_BPartner_ID=?");                   		//      #5
		if (!isMultiCurrency) {
			sql.append(" AND p.C_Currency_ID=?");				//      #6
		}
		if (orgId != 0 ) {
			sql.append(" AND p.AD_Org_ID=" + orgId);
		}
		if (apar != null
				&& !apar.equals(APAR_A)) {
			sql.append(" AND p.IsReceipt= '" + (apar.equals(APAR_R)? "Y": "N" ) +"'" );
		}
		//Openup. Nicolas Sarlabos. 01/07/2019. #12307.
		if (projectId != 0 ) {
			sql.append(" AND p.C_Project_ID=" + projectId);
		}
		//Fin #12307.
		sql.append(" ORDER BY p.DateTrx,p.DocumentNo");
		
		// role security
		sql = new StringBuffer( MRole.getDefault(Env.getCtx(), false).addAccessSQL( sql.toString(), "p", MRole.SQL_FULLYQUALIFIED, MRole.SQL_RO ) );
		
		log.fine("PaySQL=" + sql.toString());
		try
		{
			PreparedStatement pstmt = DB.prepareStatement(sql.toString(), null);
			pstmt.setBigDecimal(1, currencyRate);
			pstmt.setInt(2, currencyId);
			pstmt.setBigDecimal(3, currencyRate);
			pstmt.setInt(4, currencyId);
			pstmt.setInt(5, bPartnerId);
			if (!isMultiCurrency)
				pstmt.setInt(6, currencyId);
			ResultSet rs = pstmt.executeQuery();
			while (rs.next())
			{
				Vector<Object> line = new Vector<Object>();
				line.add(new IDColumn(rs.getInt("C_Payment_ID"))); 	//  0-C_Payment_ID
				line.add(rs.getTimestamp("DateTrx"));       	//  1-TrxDate
				//Openup. Nicolas Sarlabos. 01/07/2019. #12307.
				int orgID = rs.getInt("AD_Org_ID"); 				// 10 AD_Org_ID
				if (orgID == 0) {
					line.add("*");
				} else {
					line.add((MOrg.get(Env.getCtx(), orgID).getName()));
				}
				if(rs.getString("IsReceipt").equals("Y"))			//  Ar/Ap
					line.add("AR");
				else
					line.add("AP");

				int projectID = rs.getInt("C_Project_ID");
				if(projectID > 0){
					line.add((MProject.getById(Env.getCtx(), projectID, null)).getName());
				} else line.add(" ");

				line.add((MDocType.get(Env.getCtx(), rs.getInt("C_DocType_ID")).getPrintName(Env.getAD_Language(Env.getCtx()))));
				//Fin #12307.

				KeyNamePair pp = new KeyNamePair(rs.getInt("C_Payment_ID"), rs.getString("DocumentNo"));
				line.add(pp);                       	//  4-DocumentNo
				line.add(rs.getString("Description"));  //  5-Description
				//	
				if (isMultiCurrency)
				{
					line.add(rs.getString("ISO_Code"));      	//  6-Currency
					line.add(rs.getBigDecimal("PayAmt"));  	//  7-PayAmt
				}
				line.add(rs.getBigDecimal("ConvertedAmt"));      	//  6/8-ConvertedAmt
				BigDecimal available = rs.getBigDecimal("AvailableAmt");
				if (available == null || available.signum() == 0)	//	nothing available
					continue;
				line.add(available);					//  7/9-ConvOpen/Available
				line.add(Env.ZERO);						//  8/10-PaymentAmt

				data.add(line);
			}
			rs.close();
			pstmt.close();
		}
		catch (SQLException e)
		{
			log.log(Level.SEVERE, sql.toString(), e);
		}
		
		return data;
	}
	
	public Vector<String> getPaymentColumnNames(boolean isMultiCurrency)
	{	
		//  Header Info
		//Openup. Nicolas Sarlabos. 01/07/2019. #12307.
		Vector<String> columnNames = new Vector<String>();
		columnNames.add(Msg.getMsg(Env.getCtx(), "Select"));
		columnNames.add(Msg.translate(Env.getCtx(), "Date"));
		columnNames.add(Msg.getElement(Env.getCtx(), "AD_Org_ID"));
		columnNames.add(Msg.getElement(Env.getCtx(), "APAR"));
		columnNames.add(Util.cleanAmp(Msg.translate(Env.getCtx(), "C_Project_ID")));
		columnNames.add(Util.cleanAmp(Msg.translate(Env.getCtx(), "C_DocType_ID")));
		columnNames.add(Util.cleanAmp(Msg.translate(Env.getCtx(), "DocumentNo")));
		columnNames.add(Msg.getElement(Env.getCtx(), "Description"));
		if (isMultiCurrency)
		{
			columnNames.add(Msg.getMsg(Env.getCtx(), "TrxCurrency"));
			columnNames.add(Msg.translate(Env.getCtx(), "Amount"));
		}
		columnNames.add(Msg.getMsg(Env.getCtx(), "ConvertedAmount"));
		columnNames.add(Msg.getMsg(Env.getCtx(), "OpenAmt"));
		columnNames.add(Msg.getMsg(Env.getCtx(), "AppliedAmt"));
		//Fin #12307.

		return columnNames;
	}
	
	public void setPaymentColumnClass(IMiniTable paymentTable, boolean isMultiCurrency)
	{
		//Openup. Nicolas Sarlabos. 01/07/2019. #12307.
		Vector<String> names = getPaymentColumnNames(isMultiCurrency);
		int i = 0;
		paymentTable.setKeyColumnIndex(i);
		paymentTable.setColumnClass(i, IDColumn.class, true, names.get(i++));         	//  0-Selection
		paymentTable.setColumnClass(i, Timestamp.class, true, names.get(i++));        	//  1-TrxDate
		paymentTable.setColumnClass(i, String.class, true, names.get(i++));        		//  Org
		paymentTable.setColumnClass(i, String.class, true, names.get(i++));        		//  Ar/Ap
		paymentTable.setColumnClass(i, String.class, false, names.get(i++));      //proyecto
		paymentTable.setColumnClass(i, String.class, false, names.get(i++));   //tipo de documento
		paymentTable.setColumnClass(i, String.class, true, names.get(i++));           	//  2-Value
		paymentTable.setColumnClass(i, String.class, true, names.get(i++));           	//  3-Description
		if (isMultiCurrency)
		{
			paymentTable.setColumnClass(i, String.class, true, names.get(i++));       	//  4-Currency
			paymentTable.setColumnClass(i, BigDecimal.class, true, names.get(i++));   	//  5-PayAmt
		}
		paymentTable.setColumnClass(i, BigDecimal.class, true, names.get(i++));       	//  6-ConvAmt
		paymentTable.setColumnClass(i, BigDecimal.class, true, names.get(i++));       	//  7-ConvOpen
		paymentTable.setColumnClass(i, BigDecimal.class, false, names.get(i++));      	//  8-Allocated
		//Fin #12307.
		//
		paymentPaidIndex = isMultiCurrency ? 12 : 10;
		//  Table UI
		paymentTable.autoSize();
	}
	
	public Vector<Vector<Object>> getInvoiceData(boolean isMultiCurrency, Object date, IMiniTable invoiceTable, BigDecimal currencyRate)
	{
		/********************************
		 *  Load unpaid Invoices
		 *      1-TrxDate, 2-Value, (3-Currency, 4-InvAmt,)
		 *      5-ConvAmt, 6-ConvOpen, 7-ConvDisc, 8-WriteOff, 9-Applied
		 * 
		 SELECT i.DateInvoiced,i.DocumentNo,i.C_Invoice_ID,c.ISO_Code,
		 i.GrandTotal*i.MultiplierAP "GrandTotal", 
		 currencyConvert(i.GrandTotal*i.MultiplierAP,i.C_Currency_ID,i.C_Currency_ID,i.DateInvoiced,i.C_ConversionType_ID,i.AD_Client_ID,i.AD_Org_ID) "GrandTotal $", 
		 invoiceOpen(C_Invoice_ID,C_InvoicePaySchedule_ID) "Open",
		 currencyConvert(invoiceOpen(C_Invoice_ID,C_InvoicePaySchedule_ID),i.C_Currency_ID,i.C_Currency_ID,i.DateInvoiced,i.C_ConversionType_ID,i.AD_Client_ID,i.AD_Org_ID)*i.MultiplierAP "Open $", 
		 invoiceDiscount(i.C_Invoice_ID,SysDate,C_InvoicePaySchedule_ID) "Discount",
		 currencyConvert(invoiceDiscount(i.C_Invoice_ID,SysDate,C_InvoicePaySchedule_ID),i.C_Currency_ID,i.C_Currency_ID,i.DateInvoiced,i.C_ConversionType_ID,i.AD_Client_ID,i.AD_Org_ID)*i.Multiplier*i.MultiplierAP "Discount $",
		 i.MultiplierAP, i.Multiplier 
		 FROM C_Invoice_v i INNER JOIN C_Currency c ON (i.C_Currency_ID=c.C_Currency_ID) 
		 WHERE -- i.IsPaid='N' AND i.Processed='Y' AND i.C_BPartner_ID=1000001
		 */

		if(currencyRate == null)
			currencyRate = Env.ZERO;

		Vector<Vector<Object>> data = new Vector<Vector<Object>>();
		StringBuffer sql = new StringBuffer("SELECT i.DateInvoiced,i.DocumentNo, i.Description, i.C_Invoice_ID," //  1..3
				+ "c.ISO_Code, (i.GrandTotal*i.MultiplierAP) AS OriginalAmt, "                            //  4..5    Orig Currency
				+ "currencyConvert_receipt(i.GrandTotal*i.MultiplierAP,?,i.C_Currency_ID,?,i.AD_Client_ID,i.AD_Org_ID) AS ConvertedAmt, " //  6   #1  Converted, #2 Date
				+ "(currencyConvert_receipt(invoiceOpen(C_Invoice_ID,C_InvoicePaySchedule_ID),?,i.C_Currency_ID,?,i.AD_Client_ID,i.AD_Org_ID)*i.MultiplierAP) AS OpenAmt, "  //  7   #3, #4  Converted Open
				+ "(currencyConvert_receipt(invoiceDiscount(i.C_Invoice_ID,?,C_InvoicePaySchedule_ID),?,i.C_Currency_ID,?,i.AD_Client_ID,i.AD_Org_ID)*i.Multiplier*i.MultiplierAP) AS DiscountAmt,"               //  #5, #6
				+ "i.MultiplierAP, i.IsSoTrx, i.AD_Org_ID, i.c_doctypetarget_id, coalesce(i.c_project_id, 0) as c_project_id, coalesce(i.c_order_id, 0) as c_order_id" // 9..11 //Openup. Nicolas Sarlabos. 01/07/2019. #12307-#12922.
				+ " FROM C_Invoice_v i"		//  corrected for CM/Split
				+ " INNER JOIN C_Currency c ON (i.C_Currency_ID=c.C_Currency_ID) "
				//Openup. Nicolas Sarlabos. 11/04/2019. #11683.
				+ " INNER JOIN C_DocType d ON (i.C_DocTypeTarget_ID=d.C_DocType_ID) "
				+ "WHERE i.IsPaid='N' AND i.Processed='Y' AND d.docbasetype NOT IN ('DPI','DRI') "
				//Fin #11683.
				+ " AND i.C_BPartner_ID=?");                                            //  #7
		if (!isMultiCurrency)
			sql.append(" AND i.C_Currency_ID=?");                                   //  #8
		if (orgId != 0 ) 
			sql.append(" AND i.AD_Org_ID=" + orgId);
		//Openup. Nicolas Sarlabos. 01/07/2019. #12307.
		if (projectId != 0 ) {
			sql.append(" AND i.C_Project_ID=" + projectId);
		}//Fin #12307.
		if (apar != null
				&& !apar.equals(APAR_A)) {
			sql.append(" AND i.IsSoTrx='" + (apar.equals(APAR_R)? "Y": "N" ) +"'" );
		}
		sql.append(" ORDER BY i.DateInvoiced, i.DocumentNo");
		log.fine("InvSQL=" + sql.toString());
		
		// role security
		sql = new StringBuffer( MRole.getDefault(Env.getCtx(), false).addAccessSQL( sql.toString(), "i", MRole.SQL_FULLYQUALIFIED, MRole.SQL_RO ) );
		
		try
		{
			PreparedStatement pstmt = DB.prepareStatement(sql.toString(), null);
			pstmt.setBigDecimal(1, currencyRate);
			pstmt.setInt(2, currencyId);
			pstmt.setBigDecimal(3, currencyRate);
			pstmt.setInt(4, currencyId);
			pstmt.setTimestamp(5, (Timestamp)date);
			pstmt.setBigDecimal(6, currencyRate);
			pstmt.setInt(7, currencyId);
			pstmt.setInt(8, bPartnerId);
			if (!isMultiCurrency)
				pstmt.setInt(9, currencyId);
			ResultSet rs = pstmt.executeQuery();
			while (rs.next())
			{
				Vector<Object> line = new Vector<Object>();
				line.add(new IDColumn(rs.getInt("C_Invoice_ID"))); //  0-C_Invoice_ID
				line.add(rs.getTimestamp("DateInvoiced"));       //  1-TrxDate
				//Openup. Nicolas Sarlabos. 01/07/2019. #12307.
				int orgID = rs.getInt("AD_Org_ID"); 			// 1-AD_Org_ID
				if (orgID == 0) {
					line.add("*");
				} else {
					line.add((MOrg.get(Env.getCtx(), orgID).getName()));
				}
				if(rs.getString("IsSoTrx").equals("Y"))  //  Ar/Ap
					line.add("AR");
				else
					line.add("AP");

				int projectID = rs.getInt("C_Project_ID");
				if(projectID > 0){
					line.add((MProject.getById(Env.getCtx(), projectID, null)).getName());
				} else line.add(" ");

				//Openup. Nicolas Sarlabos. 03/12/2019. #12922.
				int orderID = rs.getInt("C_Order_ID");
				if(orderID > 0){
					MOrder o = new MOrder(Env.getCtx(), orderID, null);
					line.add(o.getDocumentNo());
				} else line.add(" ");
				//Fin #12922.

				line.add((MDocType.get(Env.getCtx(), rs.getInt("C_DocTypeTarget_ID")).getPrintName(Env.getAD_Language(Env.getCtx()))));
				//Fin #12307.

				KeyNamePair pp = new KeyNamePair(rs.getInt("C_Invoice_ID"), rs.getString("DocumentNo"));
				line.add(pp);          							//  2-Value
				line.add(rs.getString("Description"));			//	3-Description
				if (isMultiCurrency)
				{
					line.add(rs.getString("ISO_Code"));     	//  4-Currency
					line.add(rs.getBigDecimal("OriginalAmt"));  //  5-Orig Amount
				}
				line.add(rs.getBigDecimal("ConvertedAmt"));     //  4/6-ConvAmt
				BigDecimal open = rs.getBigDecimal("OpenAmt");
				if (open == null)		//	no conversion rate
					open = Env.ZERO;
				line.add(open);      							//  5/7-ConvOpen
				BigDecimal discount = rs.getBigDecimal("DiscountAmt");
				if (discount == null)	//	no conversion rate
					discount = Env.ZERO;
				line.add(discount);								//  6/8-ConvAllowedDisc
				line.add(Env.ZERO);      						//  7/9-WriteOff
				line.add(Env.ZERO);								//  8/10-Applied
				line.add(open);				    				//  9/11-OverUnder

				//	Add when open <> 0 (i.e. not if no conversion rate)
				if (Env.ZERO.compareTo(open) != 0)
					data.add(line);
			}
			rs.close();
			pstmt.close();
		}
		catch (SQLException e)
		{
			log.log(Level.SEVERE, sql.toString(), e);
		}
		
		return data;
	}

	public Vector<String> getInvoiceColumnNames(boolean isMultiCurrency)
	{
		//  Header Info
		Vector<String> columnNames = new Vector<String>();
		columnNames.add(Msg.getMsg(Env.getCtx(), "Select"));
		columnNames.add(Msg.translate(Env.getCtx(), "Date"));
		columnNames.add(Msg.getElement(Env.getCtx(), "AD_Org_ID"));
		columnNames.add(Msg.getElement(Env.getCtx(), "APAR"));
		columnNames.add(Util.cleanAmp(Msg.translate(Env.getCtx(), "C_Project_ID")));
		columnNames.add(Util.cleanAmp("Orden"));
		columnNames.add(Util.cleanAmp(Msg.translate(Env.getCtx(), "C_DocTypeTarget_ID")));
		columnNames.add(Util.cleanAmp(Msg.translate(Env.getCtx(), "DocumentNo")));
		columnNames.add(Msg.getElement(Env.getCtx(), "Description"));
		if (isMultiCurrency)
		{
			columnNames.add(Msg.getMsg(Env.getCtx(), "TrxCurrency"));
			columnNames.add(Msg.translate(Env.getCtx(), "Amount"));
		}
		columnNames.add(Msg.getMsg(Env.getCtx(), "ConvertedAmount"));
		columnNames.add(Msg.getMsg(Env.getCtx(), "OpenAmt"));
		columnNames.add(Msg.getMsg(Env.getCtx(), "Discount"));
		columnNames.add(Msg.getMsg(Env.getCtx(), "WriteOff"));
		columnNames.add(Msg.getMsg(Env.getCtx(), "AppliedAmt"));
		columnNames.add(Msg.getMsg(Env.getCtx(), "OverUnderAmt"));
		//Openup. Nicolas Sarlabos. 01/07/2019. #12307.


		//Fin #12307.

		//  Header Info
		/*Vector<String> columnNames = new Vector<String>();
		columnNames.add(Msg.getMsg(Env.getCtx(), "Select"));
		columnNames.add(Msg.translate(Env.getCtx(), "Date"));
		columnNames.add(Msg.getElement(Env.getCtx(), "APAR"));
		columnNames.add(Msg.getElement(Env.getCtx(), "AD_Org_ID"));
		columnNames.add(Util.cleanAmp(Msg.translate(Env.getCtx(), "DocumentNo")));
		columnNames.add(Msg.getElement(Env.getCtx(), "Description"));
		if (isMultiCurrency)
		{
			columnNames.add(Msg.getMsg(Env.getCtx(), "TrxCurrency"));
			columnNames.add(Msg.translate(Env.getCtx(), "Amount"));
		}
		columnNames.add(Msg.getMsg(Env.getCtx(), "ConvertedAmount"));
		columnNames.add(Msg.getMsg(Env.getCtx(), "OpenAmt"));
		columnNames.add(Msg.getMsg(Env.getCtx(), "Discount"));
		columnNames.add(Msg.getMsg(Env.getCtx(), "WriteOff"));
		columnNames.add(Msg.getMsg(Env.getCtx(), "AppliedAmt"));
		columnNames.add(Msg.getMsg(Env.getCtx(), "OverUnderAmt"));
		//Openup. Nicolas Sarlabos. 01/07/2019. #12307.
		columnNames.add(Util.cleanAmp(Msg.translate(Env.getCtx(), "C_DocTypeTarget_ID")));
		columnNames.add(Util.cleanAmp(Msg.translate(Env.getCtx(), "C_Project_ID")));*/
		//Fin #12307.
		return columnNames;
	}
	
	public void setInvoiceColumnClass(IMiniTable invoiceTable, boolean isMultiCurrency)
	{
		Vector<String> names = getInvoiceColumnNames(isMultiCurrency);
		int i = 0;
		invoiceTable.setKeyColumnIndex(i);
		invoiceTable.setColumnClass(i, IDColumn.class, true, names.get(i++));        //  0-C_Invoice_ID
		invoiceTable.setColumnClass(i, Timestamp.class, true, names.get(i++));        //  1-TrxDate
		invoiceTable.setColumnClass(i, String.class, true, names.get(i++));        //  Org
		invoiceTable.setColumnClass(i, String.class, true, names.get(i++));        //  Ar/Ap
		//Openup. Nicolas Sarlabos. 01/07/2019. #12307.
		invoiceTable.setColumnClass(i, String.class, true, names.get(i++));
		invoiceTable.setColumnClass(i, String.class, true, names.get(i++));
		invoiceTable.setColumnClass(i, String.class, true, names.get(i++));
		//Fin #12307.
		invoiceTable.setColumnClass(i, String.class, true, names.get(i++));           //  2-Value
		invoiceTable.setColumnClass(i, String.class, true, names.get(i++));           //  3-Description
		if (isMultiCurrency)
		{
			invoiceTable.setColumnClass(i, String.class, true, names.get(i++));       //  4-Currency
			invoiceTable.setColumnClass(i, BigDecimal.class, true, names.get(i++));   //  5-Amt
		}
		invoiceTable.setColumnClass(i, BigDecimal.class, true, names.get(i++));       //  6-ConvAmt
		invoiceTable.setColumnClass(i, BigDecimal.class, true, names.get(i++));       //  7-ConvAmt Open
		invoiceTable.setColumnClass(i, BigDecimal.class, false, names.get(i++));      //  8-Conv Discount
		invoiceTable.setColumnClass(i, BigDecimal.class, false, names.get(i++));      //  9-Conv WriteOff
		invoiceTable.setColumnClass(i, BigDecimal.class, false, names.get(i++));      //  10-Conv Applied
		invoiceTable.setColumnClass(i, BigDecimal.class, true, names.get(i++));		  //  11-Conv OverUnder


		/*invoiceTable.setColumnClass(i, IDColumn.class, true, names.get(i++));        //  0-C_Invoice_ID
		invoiceTable.setColumnClass(i, Timestamp.class, true, names.get(i++));        //  1-TrxDate
		invoiceTable.setColumnClass(i, String.class, true, names.get(i++));        //  Ar/Ap
		invoiceTable.setColumnClass(i, String.class, true, names.get(i++));        //  Org
		invoiceTable.setColumnClass(i, String.class, true, names.get(i++));           //  2-Value
		invoiceTable.setColumnClass(i, String.class, true, names.get(i++));           //  3-Description
		if (isMultiCurrency)
		{
			invoiceTable.setColumnClass(i, String.class, true, names.get(i++));       //  4-Currency
			invoiceTable.setColumnClass(i, BigDecimal.class, true, names.get(i++));   //  5-Amt
		}
		invoiceTable.setColumnClass(i, BigDecimal.class, true, names.get(i++));       //  6-ConvAmt
		invoiceTable.setColumnClass(i, BigDecimal.class, true, names.get(i++));       //  7-ConvAmt Open
		invoiceTable.setColumnClass(i, BigDecimal.class, false, names.get(i++));      //  8-Conv Discount
		invoiceTable.setColumnClass(i, BigDecimal.class, false, names.get(i++));      //  9-Conv WriteOff
		invoiceTable.setColumnClass(i, BigDecimal.class, false, names.get(i++));      //  10-Conv Applied
		invoiceTable.setColumnClass(i, BigDecimal.class, true, names.get(i++));		  //  11-Conv OverUnder
		//Openup. Nicolas Sarlabos. 01/07/2019. #12307.
		invoiceTable.setColumnClass(i, String.class, true, names.get(i++));
		invoiceTable.setColumnClass(i, String.class, true, names.get(i++));*/
		//Fin #12307.
		//  Table UI
		invoiceTable.autoSize();
	}
	
	/**
	 * Change Index for tables
	 * @param isMultiCurrency
	 */
	public void changeIndexForTables(boolean isMultiCurrency)
	{
		paymentOpenIndex = isMultiCurrency ? 11 : 9;
		invoiceOpenIndex = isMultiCurrency ? 12 : 10;
		invoiceDiscountIndex = isMultiCurrency ? 13 : 11;
		invoiceWriteOffIndex = isMultiCurrency ? 14 : 12;
		invoiceAppliedIndex = isMultiCurrency ? 15 : 13;
		invoiceOverUnderIndex = isMultiCurrency ? 16 : 14;
	}   //  loadBPartner
	
	public String writeOff(int row, int col, boolean isInvoice, IMiniTable payment, IMiniTable invoice, boolean isAutoWriteOff)
	{
		String msg = "";
		/**
		 *  Setting defaults
		 */
		if (calculating)  //  Avoid recursive calls
			return msg;
		calculating = true;
		
		log.config("Row=" + row 
			+ ", Col=" + col + ", InvoiceTable=" + isInvoice);
        
		//  Payments
		if (!isInvoice)
		{
			BigDecimal open = (BigDecimal)payment.getValueAt(row, payment.convertColumnIndexToView(paymentOpenIndex));
			BigDecimal applied = (BigDecimal)payment.getValueAt(row, payment.convertColumnIndexToView(paymentPaidIndex));
			
			if (col == 0)
			{
				// selection of payment row
				if (payment.isRowChecked(row))
				{
					applied = open;   //  Open Amount
					if (totalDiff.abs().compareTo(applied.abs()) < 0			// where less is available to allocate than open
							&& totalDiff.signum() == -applied.signum() )    	// and the available amount has the opposite sign
						applied = totalDiff.negate();						// reduce the amount applied to what's available
					
				}
				else    //  de-selected
					applied = Env.ZERO;
			}
			
			
			if (col == payment.convertColumnIndexToView(paymentPaidIndex))
			{
				if ( applied.signum() == -open.signum() )
					applied = applied.negate();
				if ( open.abs().compareTo( applied.abs() ) < 0 )
							applied = open;
			}
			
			payment.setValueAt(applied, row, payment.convertColumnIndexToView(paymentPaidIndex));
		}

		//  Invoice
		else 
		{
			boolean selected = invoice.isRowChecked(row);
			BigDecimal open = (BigDecimal)invoice.getValueAt(row, invoice.convertColumnIndexToView(invoiceOpenIndex));
			BigDecimal discount = (BigDecimal)invoice.getValueAt(row, invoice.convertColumnIndexToView(invoiceDiscountIndex));
			BigDecimal applied = (BigDecimal)invoice.getValueAt(row, invoice.convertColumnIndexToView(invoiceAppliedIndex));
			BigDecimal writeOff = (BigDecimal) invoice.getValueAt(row, invoice.convertColumnIndexToView(invoiceWriteOffIndex));
			BigDecimal overUnder = (BigDecimal) invoice.getValueAt(row, invoice.convertColumnIndexToView(invoiceOverUnderIndex));
			int openSign = open.signum();
			
			if (col == 0)  //selection
			{
				//  selected - set applied amount
				if ( selected )
				{
					applied = open;    //  Open Amount
					applied = applied.subtract(discount);
					writeOff = Env.ZERO;  //  to be sure
					overUnder = Env.ZERO;

					if (totalDiff.abs().compareTo(applied.abs()) < 0			// where less is available to allocate than open
							&& totalDiff.signum() == applied.signum() )     	// and the available amount has the same sign
						applied = totalDiff;									// reduce the amount applied to what's available

					if ( isAutoWriteOff )
						writeOff = open.subtract(applied.add(discount));
					else
						overUnder = open.subtract(applied.add(discount));
				}
				else    //  de-selected
				{
					writeOff = Env.ZERO;
					applied = Env.ZERO;
					overUnder = Env.ZERO;
				}
			}
			
			// check entered values are sensible and possibly auto write-off
			if ( selected && col != 0 )
			{
				
				// values should have same sign as open except possibly over/under
				if ( discount.signum() == -openSign )
					discount = discount.negate();
				if ( writeOff.signum() == -openSign)
					writeOff = writeOff.negate();
				if ( applied.signum() == -openSign )
					applied = applied.negate();
				
				// discount and write-off must be less than open amount
				if ( discount.abs().compareTo(open.abs()) > 0)
					discount = open;
				if ( writeOff.abs().compareTo(open.abs()) > 0)
					writeOff = open;
				
				
				/*
				 * Two rules to maintain:
				 *
				 * 1) |writeOff + discount| < |open| 
				 * 2) discount + writeOff + overUnder + applied = 0
				 *
				 *   As only one column is edited at a time and the initial position was one of compliance
				 *   with the rules, we only need to redistribute the increase/decrease in the edited column to 
				 *   the others.
				*/
				BigDecimal newTotal = discount.add(writeOff).add(applied).add(overUnder);  // all have same sign
				BigDecimal difference = newTotal.subtract(open);
				
				// rule 2
				BigDecimal diffWOD = writeOff.add(discount).subtract(open);
										
				if ( diffWOD.signum() == open.signum() )  // writeOff and discount are too large
				{
					if ( col == invoice.convertColumnIndexToView(invoiceDiscountIndex) )       // then edit writeoff
					{
						writeOff = writeOff.subtract(diffWOD);
					} 
					else                            // col = i_writeoff
					{
						discount = discount.subtract(diffWOD);
					}
					
					difference = difference.subtract(diffWOD);
				}
				
				// rule 1
				if ( col == invoice.convertColumnIndexToView(invoiceAppliedIndex))
					overUnder = overUnder.subtract(difference);
				else
					applied = applied.subtract(difference);
				
			}
			
			//	Warning if write Off > 30%
			if (isAutoWriteOff && writeOff.doubleValue()/open.doubleValue() > .30)
				msg = "AllocationWriteOffWarn";

			invoice.setValueAt(discount, row, invoice.convertColumnIndexToView(invoiceDiscountIndex));
			invoice.setValueAt(applied, row, invoice.convertColumnIndexToView(invoiceAppliedIndex));
			invoice.setValueAt(writeOff, row, invoice.convertColumnIndexToView(invoiceWriteOffIndex));
			invoice.setValueAt(overUnder, row, invoice.convertColumnIndexToView(invoiceOverUnderIndex));
			
			//invoice.repaint(); //  update r/o
		}

		calculating = false;
		
		return msg;
	}
	
	/**
	 *  Calculate Allocation info
	 */
	public String calculatePayment(IMiniTable payment, boolean isMultiCurrency)
	{
		log.config("");

		//  Payment
		totalPay = new BigDecimal(0.0);
		int rows = payment.getRowCount();
		noPayments = 0;
		for (int i = 0; i < rows; i++)
		{
			if (payment.isRowChecked(i))
			{
				Timestamp ts = (Timestamp)payment.getValueAt(i, payment.convertColumnIndexToView(1));
				if ( !isMultiCurrency )  // the converted amounts are only valid for the selected date
					allocDate = TimeUtil.max(allocDate, ts);
				BigDecimal bd = (BigDecimal)payment.getValueAt(i, payment.convertColumnIndexToView(paymentPaidIndex));
				totalPay = totalPay.add(bd);  //  Applied Pay
				noPayments++;
				log.fine("Payment_" + i + " = " + bd + " - Total=" + totalPay);
			}
		}
		return String.valueOf(noPayments) + " - "
			+ Msg.getMsg(Env.getCtx(), "Sum") + "  " + format.format(totalPay) + " ";
	}
	
	public String calculateInvoice(IMiniTable invoice, boolean isMultiCurrency) {		
		//  Invoices
		totalInv = new BigDecimal(0.0);
		int rows = invoice.getRowCount();
		noInvoices = 0;

		for (int i = 0; i < rows; i++)
		{
			if (invoice.isRowChecked(i))
			{
				Timestamp ts = (Timestamp)invoice.getValueAt(i, invoice.convertColumnIndexToView(1));
				if ( !isMultiCurrency )  // converted amounts only valid for selected date
					allocDate = TimeUtil.max(allocDate, ts);
				BigDecimal bd = (BigDecimal)invoice.getValueAt(i, invoice.convertColumnIndexToView(invoiceAppliedIndex));
				totalInv = totalInv.add(bd);  //  Applied Inv
				noInvoices++;
				log.fine("Invoice_" + i + " = " + bd + " - Total=" + totalPay);
			}
		}
		return String.valueOf(noInvoices) + " - "
			+ Msg.getMsg(Env.getCtx(), "Sum") + "  " + format.format(totalInv) + " ";
	}
	
	/**************************************************************************
	 *  Save Data
	 */
	public String saveData(int m_WindowNo, Object date, IMiniTable payment, IMiniTable invoice, Integer convType, BigDecimal currencyRate, boolean multiCurrency, String trxName) {
		if (noInvoices + noPayments == 0)
			return "";

		//  fixed fields
		int AD_Client_ID = Env.getContextAsInt(Env.getCtx(), m_WindowNo, "AD_Client_ID");
		int orgId = orgWriteId;
		if(orgId == 0) {
			orgId = Env.getContextAsInt(Env.getCtx(), m_WindowNo, "AD_Org_ID");
		}
		int orderId = 0;
		int cashLineId = 0;
		Timestamp DateTrx = (Timestamp)date;
		int paySelectionId = 0;

		//Openup. Nicolas Sarlabos. 02/01/2020. #13466.
		if(poFrom != null){
			if((poFrom.get_TableName()).toUpperCase().equalsIgnoreCase("C_PAYSELECTION"))
				paySelectionId = poFrom.get_ID();
		}//Fin 13466.

		//int paySelectionId = Env.getContextAsInt(Env.getCtx(), m_WindowNo, "C_PaySelection_ID");//Openup. Nicolas Sarlabos. 02/01/2020. #13466.
		if (orgId == 0) {
			throw new AdempiereException("@Org0NotAllowed@");
		}
		//
		log.config("Client=" + AD_Client_ID + ", Org=" + orgId
			+ ", BPartner=" + bPartnerId + ", Date=" + DateTrx);

		//  Payment - Loop and add them to paymentList/amountList
		int pRows = payment.getRowCount();
		ArrayList<Integer> paymentList = new ArrayList<Integer>(pRows);
		ArrayList<BigDecimal> amountList = new ArrayList<BigDecimal>(pRows);
		BigDecimal paymentAppliedAmt = Env.ZERO;
		
		paymentList = payment.getSelectedKeys();
		// Sum up the payment and applied amounts.
		for (int i = 0; i < pRows; i++)
		{
			if (payment.isRowChecked(i))
			{
				BigDecimal paymentAmt = (BigDecimal)payment.getValueAt(i, paymentPaidIndex);  //  Applied Payment
				amountList.add(paymentAmt);
				//
				paymentAppliedAmt = paymentAppliedAmt.add(paymentAmt);
				//
				log.fine("C_Payment_ID=" + payment.getRowKey(i) 
					+ " - PaymentAmt=" + paymentAmt); // + " * " + Multiplier + " = " + PaymentAmtAbs);
			}
		}
		log.config("Number of Payments=" + paymentList.size() + " - Total=" + paymentAppliedAmt);

		//  Invoices - Loop and generate allocations
		int iRows = invoice.getRowCount();
		
		//	Create Allocation
		MAllocationHdr alloc = new MAllocationHdr (Env.getCtx(), true,	//	manual
			DateTrx, currencyId, Env.getContext(Env.getCtx(), "#AD_User_Name"), trxName);
		alloc.setAD_Org_ID(orgId);
		//	Set Description
		if (!Util.isEmpty(description))
			alloc.setDescription(description);
		//Openup. Nicolas Sarlabos. 02/01/2020. #13466.
		if(paySelectionId > 0)
			alloc.set_ValueOfColumn("C_PaySelection_ID", paySelectionId);
		//Fin #13466.
		//Solop. Nicolas Sarlabos. 26/04/2021. #15981.
		if(multiCurrency){
			alloc.set_ValueOfColumn("IsMultiCurrency", multiCurrency);
			alloc.set_ValueOfColumn("C_ConversionType_ID", convType);
			alloc.set_ValueOfColumn("CurrencyRate", currencyRate);
		}//Fin #15981.
		alloc.saveEx();
		//	For all invoices
		BigDecimal unmatchedApplied = Env.ZERO;
		for (int i = 0; i < iRows; i++)
		{
			//  Invoice line is selected
			if (invoice.isRowChecked(i))
			{
				//  Invoice variables
				int C_Invoice_ID = ((IDColumn) invoice.getValueAt(i, invoice.getKeyColumnIndex())).getRecord_ID();
				BigDecimal AppliedAmt = (BigDecimal)invoice.getValueAt(i, invoiceAppliedIndex);
				//  semi-fixed fields (reset after first invoice)
				BigDecimal DiscountAmt = (BigDecimal)invoice.getValueAt(i, invoiceDiscountIndex);
				BigDecimal WriteOffAmt = (BigDecimal)invoice.getValueAt(i, invoiceWriteOffIndex);
				//	OverUnderAmt needs to be in Allocation Currency
				BigDecimal OverUnderAmt = ((BigDecimal)invoice.getValueAt(i, invoiceOpenIndex))
					.subtract(AppliedAmt).subtract(DiscountAmt).subtract(WriteOffAmt);
				
				log.config("Invoice #" + i + " - AppliedAmt=" + AppliedAmt);// + " -> " + AppliedAbs);
				//  loop through all payments until invoice applied
				
				for (int j = 0; j < paymentList.size() && AppliedAmt.signum() != 0; j++)
				{
					int paymentId = ((Integer)paymentList.get(j)).intValue();
					BigDecimal paymentAmt = (BigDecimal)amountList.get(j);
					if (paymentAmt.signum() == AppliedAmt.signum())	// only match same sign (otherwise appliedAmt increases)
					{												// and not zero (appliedAmt was checked earlier)
						log.config(".. with payment #" + j + ", Amt=" + paymentAmt);
						
						BigDecimal amount = AppliedAmt;
						if (amount.abs().compareTo(paymentAmt.abs()) > 0)  // if there's more open on the invoice
							amount = paymentAmt;							// than left in the payment
						
						//	Allocation Line
						MAllocationLine aLine = new MAllocationLine (alloc, amount, 
							DiscountAmt, WriteOffAmt, OverUnderAmt);
						aLine.setDocInfo(bPartnerId, orderId, C_Invoice_ID);
						aLine.setPaymentInfo(paymentId, cashLineId);
						aLine.saveEx();

						//  Apply Discounts and WriteOff only first time
						DiscountAmt = Env.ZERO;
						WriteOffAmt = Env.ZERO;
						//  subtract amount from Payment/Invoice
						AppliedAmt = AppliedAmt.subtract(amount);
						paymentAmt = paymentAmt.subtract(amount);
						log.fine("Allocation Amount=" + amount + " - Remaining  Applied=" + AppliedAmt + ", Payment=" + paymentAmt);
						amountList.set(j, paymentAmt);  //  update
					}	//	for all applied amounts
				}	//	loop through payments for invoice
				
				if ( AppliedAmt.signum() == 0 && DiscountAmt.signum() == 0 && WriteOffAmt.signum() == 0)
					continue;
				else {			// remainder will need to match against other invoices
					int C_Payment_ID = 0;
					
					//	Allocation Line
					MAllocationLine aLine = new MAllocationLine (alloc, AppliedAmt, 
						DiscountAmt, WriteOffAmt, OverUnderAmt);
					aLine.setDocInfo(bPartnerId, orderId, C_Invoice_ID);
					aLine.setPaymentInfo(C_Payment_ID, cashLineId);
					aLine.saveEx();
					log.fine("Allocation Amount=" + AppliedAmt);
					unmatchedApplied = unmatchedApplied.add(AppliedAmt);
				}
			}   //  invoice selected
		}   //  invoice loop

		// check for unapplied payment amounts (eg from payment reversals)
		for (int i = 0; i < paymentList.size(); i++)	{
			BigDecimal payAmt = (BigDecimal) amountList.get(i);
			if ( payAmt.signum() == 0 )
					continue;
			int paymentId = ((Integer)paymentList.get(i)).intValue();
			log.fine("Payment=" + paymentId  
					+ ", Amount=" + payAmt);

			//	Allocation Line
			MAllocationLine aLine = new MAllocationLine (alloc, payAmt, 
				Env.ZERO, Env.ZERO, Env.ZERO);
			aLine.setDocInfo(bPartnerId, 0, 0);
			aLine.setPaymentInfo(paymentId, 0);
			aLine.saveEx();
			unmatchedApplied = unmatchedApplied.subtract(payAmt);
		}
		
		// check for charge amount
		if (chargeId > 0 && unmatchedApplied.compareTo(Env.ZERO) != 0) {
			BigDecimal chargeAmt = totalDiff;
		
			//	Allocation Line
			MAllocationLine aLine = new MAllocationLine (alloc, chargeAmt.negate(), 
				Env.ZERO, Env.ZERO, Env.ZERO);
			aLine.set_CustomColumn("C_Charge_ID", chargeId);
			aLine.setC_BPartner_ID(bPartnerId);
			aLine.saveEx(trxName);
			unmatchedApplied = unmatchedApplied.add(chargeAmt);
		}
		
		if ( unmatchedApplied.signum() != 0 )
			log.log(Level.SEVERE, "Allocation not balanced -- out by " + unmatchedApplied );

		//	Should start WF
		if (alloc.get_ID() != 0) {
			if (!alloc.processIt(DocAction.ACTION_Complete)) //@Trifon
				throw new AdempiereException("@ProcessFailed@: " + alloc.getProcessMsg()); //@Trifon
			alloc.saveEx();
		}
		
		//  Test/Set IsPaid for Invoice - requires that allocation is posted
		for (int i = 0; i < iRows; i++) {
			//  Invoice line is selected
			if (invoice.isRowChecked(i)) {
				//  Invoice variables
				int C_Invoice_ID = ((IDColumn) invoice.getValueAt(i, invoice.getKeyColumnIndex())).getRecord_ID();
				String sql = "SELECT invoiceOpen(C_Invoice_ID, 0) "
					+ "FROM C_Invoice WHERE C_Invoice_ID=?";
				BigDecimal open = DB.getSQLValueBD(trxName, sql, C_Invoice_ID);
				if (open != null && open.signum() == 0)	{
					sql = "UPDATE C_Invoice SET IsPaid='Y' "
						+ "WHERE C_Invoice_ID=" + C_Invoice_ID;
					int no = DB.executeUpdate(sql, trxName);
					log.config("Invoice #" + i + " is paid - updated=" + no);
				} else
					log.config("Invoice #" + i + " is not paid - " + open);
			}
		}
		//  Test/Set Payment is fully allocated
		for (int i = 0; i < paymentList.size(); i++) {
			int paymentId = ((Integer)paymentList.get(i)).intValue();
			MPayment pay = new MPayment (Env.getCtx(), paymentId, trxName);
			if (pay.testAllocation())
				pay.saveEx();
			log.config("Payment #" + i + (pay.isAllocated() ? " not" : " is") 
					+ " fully allocated");
		}
		paymentList.clear();
		amountList.clear();
		//	Return
		return Msg.parseTranslation(Env.getCtx(), "@C_AllocationHdr_ID@ @Created@: " + alloc.getDocumentNo());
	}   //  saveData


	/**
	 * Obtiene la tasa de cambio segun parametros recibidos.
	 * OpenUp. Nicolas Sarlabos. 24/07/2020. Issue #11926.
	 * @param ctx
	 * @param currencyFromID
	 * @param currencyToID
	 * @param date
	 * @param conversionTypeID
	 * @return
	 */
	public BigDecimal getCurrencyRate (Properties ctx, int currencyFromID, int currencyToID, Timestamp date, int conversionTypeID){

		int schema_currency_id = new MAcctSchema(ctx, Env.getContextAsInt(ctx, "$C_AcctSchema_ID"), null).getC_Currency_ID();
		int client_ID = Env.getContextAsInt(ctx, 0, 0, "AD_Client_ID");

		BigDecimal rate = MConversionRate.getRate(currencyFromID, currencyToID, date, conversionTypeID, client_ID, 0);

		if (currencyToID != schema_currency_id) {
			if (rate != null && rate.compareTo(Env.ZERO) > 0){
				rate = Env.ONE.divide(rate, 3, BigDecimal.ROUND_HALF_UP);
			} else rate = null;
		}

		if (rate != null) {
			return rate;
		} else return Env.ZERO;
	}

}
