import com.typesafe.config.{Config, ConfigFactory}
import com.yandex.yoctodb.DatabaseFormat
import sbt.AutoPlugin
import sbt.plugins.JvmPlugin
import sbt.*
import sbt.Keys.*

import scala.meta.*
import scala.util.*
import com.yandex.yoctodb.util.buf.Buffer
import com.yandex.yoctodb.v1.immutable.V1Database
import com.yandex.yoctodb.immutable.*
import sbt.internal.util.ManagedLogger

import java.nio.file.Paths
import scala.annotation.implicitNotFound
import scala.jdk.CollectionConverters.*

@implicitNotFound("Primitive type ${T} isn't supported")
sealed abstract class PrimitiveValueType[T](val typeName: Type.Name)

object PrimitiveValueType {
  implicit object Int_ extends PrimitiveValueType[Int](Type.Name("Int"))
  implicit object Long_ extends PrimitiveValueType[Long](Type.Name("Long"))
  implicit object String_ extends PrimitiveValueType[String](Type.Name("String"))

  private val terms = Map(
    Int_.typeName.value -> Some(Int_),
    Long_.typeName.value -> Some(Long_),
    String_.typeName.value -> Some(String_),
  )

  def fromStr(columnType: String): Option[PrimitiveValueType[?]] =
    terms.getOrElse(columnType, None)
}

object ZioSchemaIndexGeneratorPlugin extends AutoPlugin {
  private val fileName = "DeriveSchemaIndex"

  override def requires: JvmPlugin.type = sbt.plugins.JvmPlugin

  override def trigger: sbt.PluginTrigger = allRequirements

  object autoImport {
    val genIndexDsl = taskKey[Seq[File]]("Generate Yoctodb index query dsl")
  }

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    //Compile / sourceGenerators += autoImport.genIndexDsl

    autoImport.genIndexDsl := {
      val managedSourceDir = (Compile / sourceManaged).value
      val config = ConfigFactory.parseFile(new File("./src/main/resources/application.conf"))
      writeFiles(genSource(config, managedSourceDir).toList, streams.value.log)
    }
  )

  def genSource(
      config: Config,
      sourceManagedPath: java.io.File
    ): Option[(scala.meta.Source, java.io.File)] =
    loadIndex()
      .map { case (sorted, filtered) =>
        Some((generateSource(config), sourceManagedPath / "query" / "dsl" / (fileName + ".scala")))
      }
      .getOrElse(None)

  def mkTypedParam(columnName: String, typeTag: PrimitiveValueType[?]) = {

    def mkParam[T: PrimitiveValueType](caseClassFieldName: String): Term.Param =
      Term.Param(
        mods = List.empty,
        name = Term.Name(caseClassFieldName),
        decltpe = Some(implicitly[PrimitiveValueType[T]].typeName),
        default = None,
      )

    typeTag match {
      case PrimitiveValueType.Int_ =>
        mkParam[Int](columnName)
      case PrimitiveValueType.Long_ =>
        mkParam[Long](columnName)
      case PrimitiveValueType.String_ =>
        mkParam[String](columnName)
    }
  }


  def generateSource(config: Config): scala.meta.Source = {
    val filters = config.getObjectList("filters").asScala.map { item =>
      val it    = item.entrySet.iterator
      val entry = it.next()
      val columnName   = entry.getKey()
      val columnType   = entry.getValue().render.replace("\"", "")
      (columnName, PrimitiveValueType.fromStr(columnType).getOrElse(throw new Exception(s"Filter($columnName) definition error")))
    }

    val sorters = config.getObjectList("sorters").asScala.map { item =>
      val it    = item.entrySet.iterator
      val entry = it.next()
      val columnName   = entry.getKey()
      val columnType   = entry.getValue().render.replace("\"", "")
      (columnName, PrimitiveValueType.fromStr(columnType).getOrElse(throw new Exception(s"Sorter($columnName) definition error")))
    }

    val filtersAndSorters = (filters ++ sorters).toList

    val typedParams = filtersAndSorters.map { case (columnName, typeTag) =>
      mkTypedParam(columnName, typeTag)
    }

    val literals =
      filtersAndSorters.map { case (name, _) => Lit.String(name) } ++ filtersAndSorters.map { case (_, tt) => tt.typeName }

    val clazzDef =
      Defn.Class(
        mods = List(Mod.Final(), Mod.Case()),
        name = Type.Name(fileName),
        tparamClause = Type.ParamClause(Nil),
        ctor = Ctor.Primary(
          mods = List.empty,
          name = Name(""),
          paramClauses = List(
            Term.ParamClause(
              values = typedParams,
              mod = None,
            )
          ),
        ),
        templ = Template(
          earlyClause = None,
          inits = Nil,
          body = Template.Body(selfOpt = None, stats = Nil),
          derives = Nil,
        ),
      )

    val valDef =
      Defn.Object(
        mods = Nil,
        name = Term.Name(fileName),
        templ = Template(
          earlyClause = None,
          inits = Nil,
          body = Template.Body(
            selfOpt = None,
            stats = List(
              Defn
                .Val
                .apply(
                  mods = List(Mod.Implicit()),
                  pats = List(Pat.Var(Term.Name(value = "schema"))),
                  decltpe = Some(
                    value = Type.Apply(
                      tpe = Type.Select(
                        Term.Select(
                          Term.Select(
                            Term.Select(Term.Name("zio"), Term.Name("schema")),
                            Term.Name("Schema"),
                          ),
                          Term.Name("CaseClass" + filtersAndSorters.size.toString),
                        ),
                        Type.Name("WithFields"),
                      ),
                      argClause = Type.ArgClause(
                        values = literals ++ List(
                          Type.Select(
                            Term.Select(Term.Name("query"), Term.Name("dsl")),
                            Type.Name(fileName),
                          )
                        )
                      ),
                    )
                  ),
                  rhs = Term.ApplyType(
                    fun = Term.Select(Term.Name("DeriveSchema"), Term.Name("gen")),
                    targClause = Type.ArgClause(List(Type.Name(fileName))),
                  ),
                )
            ),
          ),
        ),
      )

    Source(
      stats = List(
        Pkg(
          ref = Term.Select(Term.Name("query"), Term.Name("dsl")),
          body = Pkg.Body(
            List(
              Import(importers =
                List(
                  Importer(
                    ref = Term.Select(Term.Name("zio"), Term.Name("schema")),
                    importees = List(Importee.Name(Name.Indeterminate("DeriveSchema"))),
                  )
                )
              ),
              clazzDef,
              valDef,
            )
          ),
        )
      )
    )
  }

  def loadIndex(): Either[Throwable, (java.util.Set[String], java.util.Set[String])] =
    Try {
      val indexPath = "indexes/games"
      val indexFile = Paths.get(indexPath).toFile()
      if (indexFile.exists() && indexFile.isFile()) {
        val reader = DatabaseFormat.getCurrent().getDatabaseReader()
        val db: V1Database = reader.from(Buffer.mmap(indexFile, false)).asInstanceOf[V1Database]
        println(
          s"★ ★ ★ Loaded index from:${indexFile} [${indexFile.length() / (1024 * 1024)}MB / NumOfDocs: ${db.getDocumentCount()} ] ★ ★ ★\n"
        )

        val sortersField = db.getClass().getDeclaredField("sorters")
        sortersField.setAccessible(true)
        val sorters = sortersField.get(db).asInstanceOf[java.util.Map[String, SortableIndex]]
        println("★ ★ ★  Sorters ★ ★ ★")
        sorters.keySet().forEach { (skey: String) =>
          println(skey + " -> " + sorters.get(skey))
        }
        println("★ ★ ★ ★ ★ ★ ★ ★ ★ ★ ★ ★\n")

        val filtersField = db.getClass().getDeclaredField("filters")
        filtersField.setAccessible(true)
        val filters = filtersField.get(db).asInstanceOf[java.util.Map[String, FilterableIndex]]
        println("★ ★ ★  Filters  ★ ★ ★")
        filters.keySet().forEach { (fkey: String) =>
          println(fkey + " -> " + filters.get(fkey))
        }
        println("★ ★ ★ ★ ★ ★ ★ ★ ★ ★ ★ ★\n")

        val storersField = db.getClass().getDeclaredField("storers")
        storersField.setAccessible(true)
        val storers = storersField.get(db).asInstanceOf[java.util.Map[String, StoredIndex]]
        println("★ ★ ★  Storers  ★ ★ ★")
        storers.keySet().forEach { (skey: String) =>
          println(skey + " -> " + storers.get(skey))
        }
        println("★ ★ ★ ★ ★ ★ ★ ★ ★ ★ ★ ★\n")

        (sorters.keySet(), filters.keySet())

      }
      else throw new Exception(s"Couldn't find or open file $indexPath")
    }.toEither

  def writeFiles(
    outputs: List[(scala.meta.Source, java.io.File)],
    log: ManagedLogger,
  ): List[java.io.File] = {
    import java.nio.file.Files
    import java.nio.file.Path
    import java.io.FileOutputStream

    log.info("★ ★ ★  Generated files ★ ★ ★")
    val genFiles =
      outputs.map {
        case (src, dest) =>
          Files.createDirectories(Path.of(dest.getParent()))
          Using.resource(new FileOutputStream(dest))(_.write(src.syntax.getBytes()))
          dest
      }

    genFiles.map(_.getAbsolutePath).foreach(log.info(_))
    log.info("★ ★ ★ ★ ★ ★")
    genFiles
  }

  /*def mkFilterableParam[T: PrimitiveValueType](
      indexFieldName: String,
      caseClassFieldName: String,
    )(implicit
      ev: PrimitiveValueType[T]
    ): Term.Param = {
    val termType = Type.Name("Filterable")
    val termTypeParamType = ev.tp

    val rightHs =
      q"""
          new ${scala.meta.Init(termType, termType, Seq.empty)}[${termTypeParamType}] {
            val fieldName = $indexFieldName

            def =:=(v: ${termTypeParamType}): TermCondition =
              QueryBuilder.eq(fieldName, UnsignedByteArrays.from(v))

            def in(vs: scala.collection.immutable.Set[${termTypeParamType}]): TermCondition =
              QueryBuilder.in(fieldName, vs.toSeq.map(UnsignedByteArrays.from(_)):_*)

            def >(v: ${termTypeParamType}): TermCondition =
             QueryBuilder.gt(fieldName, UnsignedByteArrays.from(v))

            def >=(v: ${termTypeParamType}): TermCondition =
              QueryBuilder.gte(fieldName, UnsignedByteArrays.from(v))

            def <(v: ${termTypeParamType}): TermCondition =
              QueryBuilder.lt(fieldName, UnsignedByteArrays.from(v))

            def <=(v: ${termTypeParamType}): TermCondition =
              QueryBuilder.lte(fieldName, UnsignedByteArrays.from(v))
          }
      """

    Term.Param(
      mods = List(Mod.ValParam()),
      name = Term.Name(caseClassFieldName),
      decltpe = Some(Type.Apply(termType, Type.ArgClause(List(termTypeParamType)))),
      default = Some(rightHs),
    )
  }

  def mkBothParam[T: PrimitiveValueType](
      indexFieldName: String,
      caseClassFieldName: String,
    )(implicit
      ev: PrimitiveValueType[T]
    ): Term.Param = {
    val termType = Type.Name("Both")
    val termTypeParamType = ev.tp
    val rightHs =
      q"""
          new ${scala.meta.Init(termType, termType, Seq.empty)}[${termTypeParamType}] {
            val fieldName = $indexFieldName

            def =:=(v: ${termTypeParamType}): TermCondition =
              QueryBuilder.eq(fieldName, UnsignedByteArrays.from(v))

            def in(vs: scala.collection.immutable.Set[${termTypeParamType}]): TermCondition =
              QueryBuilder.in(fieldName, vs.toSeq.map(UnsignedByteArrays.from(_)):_*)

            def >(v: ${termTypeParamType}): TermCondition =
             QueryBuilder.gt(fieldName, UnsignedByteArrays.from(v))

            def >=(v: ${termTypeParamType}): TermCondition =
              QueryBuilder.gte(fieldName, UnsignedByteArrays.from(v))

            def <(v: ${termTypeParamType}): TermCondition =
              QueryBuilder.lt(fieldName, UnsignedByteArrays.from(v))

            def <=(v: ${termTypeParamType}): TermCondition =
              QueryBuilder.lte(fieldName, UnsignedByteArrays.from(v))

            def desc(): com.yandex.yoctodb.query.Order = QueryBuilder.desc(fieldName)
            def asc(): com.yandex.yoctodb.query.Order = QueryBuilder.asc(fieldName)
          }
      """

    Term.Param(
      mods = List(Mod.ValParam()),
      name = Term.Name(caseClassFieldName),
      decltpe = Some(Type.Apply(termType, Type.ArgClause(List(termTypeParamType)))),
      default = Some(rightHs),
    )

  }

  def mkSortableParam[T: PrimitiveValueType](
      indexFieldName: String,
      caseClassFieldName: String,
    )(implicit
      ev: PrimitiveValueType[T]
    ): Term.Param = {
    val termType = Type.Name("Sortable")
    val termTypeParamType = ev.tp

    val rightHs =
      q"""
          new ${scala.meta.Init(termType, termType, Seq.empty)}[${termTypeParamType}] {
            val fieldName = $indexFieldName
            def desc(): com.yandex.yoctodb.query.Order = QueryBuilder.desc(fieldName)
            def asc(): com.yandex.yoctodb.query.Order = QueryBuilder.asc(fieldName)
          }
      """

    Term.Param(
      mods = List(Mod.ValParam()),
      name = Term.Name(caseClassFieldName),
      decltpe = Some(Type.Apply(termType, Type.ArgClause(List(termTypeParamType)))),
      default = Some(rightHs),
    )
  }*/
}
