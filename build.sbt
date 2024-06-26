import org.scalajs.jsenv.nodejs.NodeJSEnv

import org.scalajs.linker.interface.ESVersion
import org.scalajs.linker.interface.OutputPatterns

val scalaV = "2.12.19"

// Include wasm.jvm on the classpath used to dynamically load Scala.js linkers
Global / scalaJSLinkerImpl / fullClasspath :=
  (wasm.jvm / Compile / fullClasspath).value

inThisBuild(Def.settings(
  scalacOptions ++= Seq(
    "-encoding",
    "utf-8",
    "-feature",
    "-deprecation",
    "-Xfatal-warnings",
  ),
  scalaJSLinkerConfig ~= {
    _.withESFeatures(_.withESVersion(ESVersion.ES2016))
  },
  jsEnv := {
    // Enable support for exnref and try_table
    new NodeJSEnv(
      NodeJSEnv.Config().withArgs(List("--experimental-wasm-exnref"))
    )
  },
))

lazy val cli = project
  .in(file("cli"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name := "cli",
    scalaVersion := scalaV,
    scalaJSUseMainModuleInitializer := true,
    scalaJSLinkerConfig ~= {
      _.withModuleKind(ModuleKind.CommonJSModule),
    },
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scala-js-macrotask-executor" % "1.1.1"
    ),
  )
  .dependsOn(
    wasm.js,
    // tests // for TestSuites constant
  )

lazy val wasm = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Pure)
  .in(file("wasm"))
  .settings(
    name := "wasm",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scalaV,
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-linker" % "1.16.0"
    ),
  )

lazy val sample = project
  .in(file("sample"))
  .enablePlugins(WasmLinkerPlugin)
  .settings(
    scalaVersion := scalaV,
    scalaJSUseMainModuleInitializer := true,
  )

lazy val testSuite = project
  .in(file("test-suite"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    scalaVersion := scalaV,
    scalaJSUseMainModuleInitializer := true,
    Compile / jsEnv := {
      val cp = Attributed
        .data((Compile / fullClasspath).value)
        .mkString(";")
      val env = Map(
        "SCALAJS_CLASSPATH" -> cp,
        "SCALAJS_MODE" -> "testsuite",
      )
      new NodeJSEnv(
        NodeJSEnv.Config()
          .withEnv(env)
          .withArgs(List("--enable-source-maps", "--experimental-wasm-exnref"))
      )
    },
    Compile / jsEnvInput := (`cli` / Compile / jsEnvInput).value
  )

lazy val tests = project
  .in(file("tests"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    scalaVersion := scalaV,
    libraryDependencies ++= Seq(
      "org.scalameta" %%% "munit" % "0.7.29" % Test,
      "org.scala-js" %%% "scala-js-macrotask-executor" % "1.1.1" % Test
    ),
    scalaJSLinkerConfig ~= {
      // Generate CoreTests as an ES module so that it can import the main.mjs files
      // Give it an `.mjs` extension so that Node.js actually interprets it as an ES module
      _.withModuleKind(ModuleKind.ESModule)
        .withOutputPatterns(OutputPatterns.fromJSFile("%s.mjs")),
    },
    test := Def.sequential(
      (testSuite / Compile / run).toTask(""),
      (Test / test)
    ).value
  ).dependsOn(cli)
