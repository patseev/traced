name := "Tracer"

version := "0.1"

scalaVersion := "2.13.3"

resolvers += Resolver.bintrayRepo("ovotech", "maven")

val betterMonadicFor = "com.olegpy"     %% "better-monadic-for" % "0.3.1"
val kindProjector    = "org.typelevel"  %% "kind-projector"     % "0.11.0" cross CrossVersion.patch
val paradise         = "org.scalamacros" % "paradise"           % "2.1.1" cross CrossVersion.patch

val Http4sVersion      = "0.21.6"
val effectUtilsVersion = "2.5.0"

libraryDependencies ++= Seq(
  "ru.tinkoff"           %% "tofu"                % "0.8.0",
  "org.tpolecat"         %% "natchez-core"        % "0.0.13",
  "org.tpolecat"         %% "natchez-log"         % "0.0.13",
  "io.chrisdavenport"    %% "log4cats-slf4j"      % "1.1.1",
  "org.http4s"           %% "http4s-blaze-server" % Http4sVersion,
  "org.http4s"           %% "http4s-circe"        % Http4sVersion,
  "org.http4s"           %% "http4s-dsl"          % Http4sVersion,
  "com.ovoenergy.effect" %% "natchez-http4s"      % effectUtilsVersion,
  compilerPlugin(kindProjector),
  compilerPlugin(betterMonadicFor),
)

scalacOptions ++= Seq("-language:higherKinds", "-Ydelambdafy:inline", "-Ymacro-annotations")
