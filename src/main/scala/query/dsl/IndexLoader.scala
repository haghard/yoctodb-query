package query.dsl

import com.yandex.yoctodb.DatabaseFormat
import com.yandex.yoctodb.util.buf.Buffer
import com.yandex.yoctodb.v1.immutable.V1Database
import org.slf4j.LoggerFactory

import java.nio.file.Paths
import scala.util.Try

trait IndexLoader {
  lazy val logger = LoggerFactory.getLogger("app")

  def loadIndex(indexPath: String): Try[V1Database] =
    Try {
      val indexFile = Paths.get(indexPath).toFile
      if (indexFile.exists && indexFile.isFile) {
        val reader = DatabaseFormat.getCurrent().getDatabaseReader()
        val db = reader.from(Buffer.mmap(indexFile, false)).asInstanceOf[V1Database]
        logger.warn(
          s"★ ★ ★ Index {} size: {}MB contains {} documents   ★ ★ ★",
          indexPath,
          indexFile.length() / (1024 * 1024),
          db.getDocumentCount(),
        )
        db
      }
      else throw new Exception(s"Couldn't find or open file $indexPath")
    }

}
