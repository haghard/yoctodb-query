import com.yandex.yoctodb.DatabaseFormat
import com.yandex.yoctodb.immutable.Database
import com.yandex.yoctodb.query.{QueryBuilder => yocto}
import com.yandex.yoctodb.util.buf.Buffer
import com.yandex.yoctodb.v1.immutable.V1Database
import org.slf4j.LoggerFactory

import java.nio.file.Paths
import scala.util.Try
import query.dsl._
import Col._

/*
  Codegen capabilities are inspired by https://github.com/blast-hardcheese/talks/tree/2023-codegen-domain-separation
*/
object Program extends App {

  val logger = LoggerFactory.getLogger("app")

  import SearchIndex._

  val query =
    yocto
      .select
      .where(
        yocto.and(
          index.column[GameStage].ops.in(Set("season-18-19", "season-19-20")),
          yocto.or(index.column[HomeTeam].ops =:= "lal", index.column[AwayTeam].ops =:= "lal"),
        )
      )
      .orderBy(index.column[GameTime].ops.desc())
      .limit(10)

  def loadIndex(): Try[V1Database] =
    Try {
      val indexPath = "indexes/games"
      val indexFile = Paths.get(indexPath).toFile
      if (indexFile.exists && indexFile.isFile) {
        val reader = DatabaseFormat.getCurrent().getDatabaseReader()
        val db = reader.from(Buffer.mmap(indexFile, false)).asInstanceOf[V1Database]
        logger.warn("★ ★ ★    Index size: {} MB. Docs number: {}  ★ ★ ★", indexFile.length() / (1024 * 1024), db.getDocumentCount())
        db
      } else throw new Exception(s"Couldn't find or open file $indexPath")
    }

  loadIndex().map { searchIndex: V1Database =>
    searchIndex.execute(
      query,
      (docId: Int, _: Database) => {
        val payload: com.yandex.yoctodb.util.buf.Buffer = searchIndex.getFieldValue(docId, "g_payload")
        //val result = NbaResultPB.parseFrom(new com.yandex.yoctodb.util.buf.BufferInputStream(payload))
        logger.debug(s"DocId: $docId ${payload.toByteArray.length}")
        true
      }
    )
  }
}
