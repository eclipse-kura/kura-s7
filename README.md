# kura-s7

Eclipse Kura™ S7 addon.

The addon provides the **S7 PLC Communication Driver**, an
[Eclipse Kura Driver](https://eclipse-kura.github.io/kura/docs-develop/connect-field-devices/driver-and-assets/)
that reads and writes the data blocks of Siemens S7 PLCs over the S7
communication protocol. It plugs into the Kura Asset/Driver model, so channels
can be configured from the Kura web console and consumed by Wires, the Asset
REST API or any other Driver consumer.

The Driver used to live in the [Eclipse Kura](https://github.com/eclipse-kura/kura)
monorepo (`kura/org.eclipse.kura.driver.s7plc.provider`) and used to be shipped
inside the Kura core deployment package. It now lives here and is released as a
standalone Debian package.

## Contents

| Module | Artifact | Description |
|---|---|---|
| `org.eclipse.kura.driver.s7plc.provider` | bundle | the Driver. Embeds `org.eclipse.kura.driver.block` (the block-oriented Driver toolkit), which is not part of the Kura runtime |
| `bom` | `kura-s7-bom` | the bundles released by this project |
| `distrib` | `kura-s7-distrib` | Debian packaging (`jdeb`) |
| `tests` | `kura-s7-tests` | unit tests and OSGi integration tests |

The build is **Maven + [bnd](https://bnd.bndtools.org/)** targeting Java 21 —
there is no Tycho and no target definition. The project was bootstrapped with
[`kura-archetype`](https://github.com/eclipse-kura/kura-archetype).

Declarative Services and Metatype descriptors are hand-written and kept in
source control under `org.eclipse.kura.driver.s7plc.provider/OSGI-INF/`.

## Prerequisites

| | |
|---|---|
| **JDK 21** | the project sets `maven.compiler.release=21` |
| **Maven 3.9.x** | |
| **git** | `git-commit-id-maven-plugin` stamps the commit hash into the snapshot Debian version |

## Building

```bash
mvn clean install
```

Add `-Presolve-integration-tests` whenever `-runrequires` or the bundle imports
change: the profile runs `bnd-resolver-maven-plugin:resolve`, which recomputes
the `-runbundles` list of
`tests/org.eclipse.kura.driver.s7plc.provider.test/integration-test.bndrun`.
Commit the resolved `.bndrun`.

```bash
mvn clean install -Presolve-integration-tests
```

### Tests

Both test kinds run as part of `mvn verify`/`mvn install`:

- **unit tests** — `maven-surefire-plugin`, from
  `tests/org.eclipse.kura.driver.s7plc.provider.test/src/test/java`;
- **OSGi integration tests** — `bnd-testing-maven-plugin`, from
  `.../src/main/java` (they are part of the test bundle). They start an embedded
  Kura framework, create an instance of the Driver through the Configuration
  Service and check that it publishes a `Driver` service with the expected
  channel descriptor. No S7 PLC is needed: the Driver connects lazily, on the
  first read or write.

Reports land in `tests/org.eclipse.kura.driver.s7plc.provider.test/target/surefire-reports/`
(unit) and `.../surefire-reports/integration-test/` (OSGi); JaCoCo writes to
`.../target/site/jacoco-aggregate/`.

## Debian package

`jdeb` is bound to the `package` phase, so every `mvn package`/`install`
produces `distrib/target/deb/kura-s7_<version>-<revision>_all.deb`.

| Build | Version | Command |
|---|---|---|
| development (default) | `2.0.0~git202608170837.bde582f-1` | `mvn clean install` |
| release | `2.0.0-1` | `mvn clean install -DreleaseBuild` |

`-DreleaseBuild` also activates the enforcer rule that fails the build if the
project version is still a `-SNAPSHOT`.

The package depends on `kura-core (>= 6.0.0~), kura-core (<< 7.0.0~)` and
installs two bundles:

| Path | Bundle |
|---|---|
| `/opt/eclipse/kura/plugins/5s/` | `org.moka7` — the Moka7 S7 library, not part of the Kura runtime |
| `/opt/eclipse/kura/plugins/6s/` | `org.eclipse.kura.driver.s7plc.provider` — the Driver, same start level as the Kura drivers |

Install it on a device and restart Kura:

```bash
apt install ./kura-s7_<version>_all.deb
systemctl restart kura
```

## Using the Driver

Create an instance from the Kura web console (**Drivers and Assets ▸ New
Driver**) selecting the `org.eclipse.kura.driver.s7plc` factory, then configure:

| Property | Description | Default |
|---|---|---|
| `host.ip` | S7 PLC host IP address | `0` |
| `rack` | S7 PLC rack | `0` |
| `slot` | S7 PLC slot | `2` |
| `authenticate` | send the session password to the PLC | `false` |
| `password` | the session password | |
| `read.minimum.gap.size` | if non zero, read requests for non consecutive addresses are aggregated when their distance is smaller than this value, in bytes | `0` |

Each channel of an Asset bound to this Driver takes:

| Property | Description |
|---|---|
| `s7.data.type` | S7 data type of the element: `BOOL`, `BYTE`, `WORD`, `DWORD`, `INT`, `DINT`, `REAL`, `CHAR` |
| `data.block.no` | DB number |
| `offset` | offset inside the data block |
| `byte.count` | number of bytes to read |
| `bit.index` | bit index, `0`–`7`, for bit-wise access |

## Contributing

See the [Kura contribution guide](https://github.com/eclipse-kura/kura/blob/develop/CONTRIBUTING.md).
Pull request titles must follow the
[Conventional Commits](https://www.conventionalcommits.org/) format, and signing
the [Eclipse Contributor Agreement](https://www.eclipse.org/legal/ECA.php) is
required.

## License

[Eclipse Public License 2.0](https://www.eclipse.org/legal/epl-2.0/)
