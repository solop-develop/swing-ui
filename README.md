# ADempiere Swing UI

<p align="center">
  <a href="https://adoptium.net/es/temurin/releases/?version=11">
    <img src="https://badgen.net/badge/Java/11/orange" alt="Java">
  </a>
  <a href="https://github.com/adempiere/swing-ui/actions/workflows/build.yml">
    <img src="https://github.com/adempiere/swing-ui/actions/workflows/build.yml/badge.svg" alt="Build GH Action">
  </a>
  <a href="https://github.com/adempiere/swing-ui/blob/master/LICENSE">
    <img src="https://img.shields.io/badge/license-GNU/GPL%20(v2)-blue" alt="License">
  </a>
  <a href="https://github.com/adempiere/swing-ui/releases/latest">
    <img src="https://img.shields.io/github/release/adempiere/swing-ui.svg" alt="GitHub release">
  </a>
  <a href="https://discord.gg/T6eH6A7PJZ">
    <img src="https://badgen.net/badge/discord/join%20chat" alt="Discord">
  </a>
</p>

A swing User Interface based in adempiere box

This project just treat of run ADempiere Swing UI based on base adempiere box project using gradle


## Requirements

This project is a java client using swing interface and completely based on gradle package management

The follow requirements need for run it:

- [Java 11 or higher](https://adoptopenjdk.net/)
- [Gradle](https://gradle.org/install/)

## Runing as development
### Clean
```shell
gradle clean
```

### Execute Swing-UI
With default connection properties file (`$HOME/Adempiere.properties`)
```shell
gradle run
```

With custom connection properties file

```shell
# As System Property
gradle run -DPropertyFile=/tmp/TEMPLATE.properties
```

## Where is the releases?

Just go to [Releases](https://github.com/adempiere/adempiere-swing-ui/releases)

## Generated Runnables

### Compile distribution files (binaries)

Just need build with gradle using the follow command for build

```shell
gradle clean
```

```Shell
gradle build
```

For complete release just run itL

```Shell
gradle createRelease
```

All asset is located in `adempiere-swing-ui/build/distributions` with a list like:

- adempiere-swing-ui.zip
- adempiere-swing-ui.tar

### How to run it?

Just unzip any compress file example `adempiere-swing-ui.zip` with the follow command:

Go to build dir

```Shell
cd build/distributions
```

Unzip it

```Shell
unzip adempiere-swing-ui.zip
```

```Shell
Archive:  adempiere-swing-ui.zip
   creating: adempiere-swing-ui/
   creating: adempiere-swing-ui/lib/
  inflating: adempiere-swing-ui/lib/adempiere-swing-ui.jar  
  inflating: adempiere-swing-ui/lib/groovy-all-2.4.15.jar  
  ...
```

Go to files

```Shell
cd adempiere-swing-ui/bin
```

The binary created are:

Linux / Mac:

- adempiere-swing-ui: A Swing interface
- run-migrate: Used for migrate from XML's
- run-setup: Setup based on old implementation
- run-silent-setup: Silent setup based on old implementation
- translation: Import / Export translations

Windows:

- adempiere-swing-ui.bat: A Swing interface
- run-migrate.bat: Used for migrate from XML's
- run-setup.bat: Setup based on old implementation
- run-silent-setup.bat: Silent setup based on old implementation
- translation.bat: Import / Export translations

## A preview

```Shell
./adempiere-swing-ui
```

![Swing Example](docs/adempiere-swing-ui.gif)


## Run (or Debug) on Visual Studio Code

### Requirements
Extensions to Install:
- [Extension Pack for Java](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-java-pack)
- [Gradle for Java](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-gradle)

#### Execute
![Swing Example Visual Studio Code](./docs/adempiere-swing-ui-debug-vs-code.gif)
