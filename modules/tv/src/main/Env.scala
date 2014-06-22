package lila.tv

import com.typesafe.config.Config

import lila.common.PimpedConfig._

import scala.collection.JavaConversions._

final class Env(
    config: Config,
    hub: lila.hub.Env,
    system: akka.actor.ActorSystem,
    scheduler: lila.common.Scheduler,
    isProd: Boolean) {

  private val FeaturedContinue = config duration "featured.continue"
  private val FeaturedDisrupt = config duration "featured.disrupt"
  private val StreamingSearch = config duration "streaming.search"
  private val UstreamApiKey = config getString "streaming.ustream_api_key"
  private val Whitelist = (config getStringList "streaming.whitelist").toSet

  lazy val featured = new Featured(
    lobbySocket = hub.socket.lobby,
    rendererActor = hub.actor.renderer,
    system = system)

  private lazy val streaming = new Streaming(
    system = system,
    renderer = hub.actor.renderer,
    ustreamApiKey = UstreamApiKey,
    whitelist = Whitelist)

  def streamsOnAir = streaming.onAir

  {
    import scala.concurrent.duration._

    scheduler.message(isProd.fold(FeaturedContinue, 10.seconds)) {
      featured.actor -> Featured.Continue
    }

    scheduler.message(FeaturedDisrupt) {
      featured.actor -> Featured.Disrupt
    }

    scheduler.once(2.seconds) {
      streaming.actor ! Streaming.Search
      scheduler.message(StreamingSearch) {
        streaming.actor -> Streaming.Search
      }
    }
  }
}

object Env {

  lazy val current = "[boot] tv" describes new Env(
    config = lila.common.PlayApp loadConfig "tv",
    hub = lila.hub.Env.current,
    system = lila.common.PlayApp.system,
    scheduler = lila.common.PlayApp.scheduler,
    isProd = lila.common.PlayApp.isProd)
}

