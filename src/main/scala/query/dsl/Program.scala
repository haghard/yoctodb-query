package query.dsl

import com.yandex.yoctodb.immutable.Database
import java.nio.charset.StandardCharsets
import com.yandex.yoctodb.query.QueryBuilder as yocto

//runMain query.dsl.Program
object Program extends App with IndexLoader {

  /*val query =
    yocto
      .select
      .where(
        yocto.and(
          games_stage.$.in(Set("season-25-26", "playoff-25-26")),
          yocto.or(
            yocto.and(games_ht.$ =:= "lal", games_at.$ =:= "gsw"),
            yocto.and(games_ht.$ =:= "gsw", games_at.$ =:= "lal"),
          ),
          // yocto.or(games_ht.$ =:= "lal", games_at.$ =:= "lal"),
        )
      )
      .orderBy(games_ts.$.desc())
      .limit(100)*/

  /*val query =
    yocto
      .select
      .where(
        yocto.and(
          games_stage.$.in(Set("season-25-26", "playoff-25-26")),
          yocto.or(games_ht.$ =:= "lal", games_at.$ =:= "lal"),
        )
      )
      .orderBy(games_ts.$.desc())
      .limit(100)*/
  for {
    gamesInfoIndex <- loadIndex("indexes/gameInfo")
    gameStatIndex <- loadIndex("indexes/gameStat")
    playerStatIndex <- loadIndex("indexes/playerStat")
  } yield {
    val q =
      yocto
        .select
        .where(gameInfo.games_stage.$.=:=("playoff-25-26"))
        .orderBy(gameInfo.games_ts.$.desc())
        .limit(100)

    gamesInfoIndex.executeAndUnlimitedCount(
      q,
      (docId: Int, db: Database) => {
        val gamePayload = gamesInfoIndex.getFieldValue(docId, "g_payload")
        val gameInfo = gamesInfoIndex.getFieldValue(docId, "g_info")
        val gameIdBuf = gamesInfoIndex.getFieldValue(docId, "g_gameId")

        val gameId = new String(gameIdBuf.toByteArray(), StandardCharsets.UTF_8)

        val gameInfoPb = basket
          .domain
          .v1
          .NbaResultPB
          .parseFrom(new com.yandex.yoctodb.util.buf.BufferInputStream(gamePayload))

        logger.warn(
          s"★ ★ ★ ${new String(gameInfo.toByteArray(), StandardCharsets.UTF_8)} ★ ★ ★"
        )
        logger.debug(gameInfoPb.toString)

        gameStatIndex.executeAndUnlimitedCount(
          yocto.select.where(gameStat.teams_gameId.$.=:=(gameId)),
          (docId: Int, db: Database) => {
            val teamStatsPayload = db.getFieldValue(docId, "team_payload")
            val totalPb = basket
              .domain
              .v1
              .TotalPB
              .parseFrom(new com.yandex.yoctodb.util.buf.BufferInputStream(teamStatsPayload))

            logger.debug(
              s"${new String(db.getFieldValue(docId, "opponent").toByteArray)} / ${totalPb.toString}"
            )

            playerStatIndex.executeAndUnlimitedCount(
              yocto.select.where(playerStat.players_gameId.$.=:=(gameId)),
              (docId: Int, db: Database) => {
                val payloadBuf = db.getFieldValue(docId, "pl_payload")
                val playerNameBuf = db.getFieldValue(docId, "pl_name")
                val playerTeamBuf = db.getFieldValue(docId, "players_team")
                val payloadPb = basket
                  .domain
                  .v1
                  .PlayerLinePB
                  .parseFrom(new com.yandex.yoctodb.util.buf.BufferInputStream(payloadBuf))
                logger.debug(
                  s"${new String(playerNameBuf.toByteArray)} / ${new String(playerTeamBuf.toByteArray)} / ${payloadPb.toString}"
                )
                true
              },
            )
            true
          },
        )
        true
      },
    )

    println(HeapUtils.logNativeMemory())
  }

}
