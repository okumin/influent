package org.apache.spark.streaming.fluentd

import java.net.InetSocketAddress
import java.util.concurrent.CompletableFuture

import influent.EventStream
import influent.forward.{ForwardCallback, ForwardServer}
import org.apache.spark.internal.Logging
import org.apache.spark.storage.StorageLevel
import org.apache.spark.streaming.fluentd.FluentdReceiver.SparkStreamingCallback
import org.apache.spark.streaming.receiver.Receiver

import scala.util.control.NonFatal

private[fluentd] class FluentdReceiver(host: String,
                                       port: Int,
                                       storageLevel: StorageLevel)
  extends Receiver[EventStream](storageLevel) with Logging {
  private[this] var server: Option[ForwardServer] = None

  override def onStart(): Unit = {
    synchronized {
      server match {
        case None =>
          val forwardServer = new ForwardServer.Builder(new SparkStreamingCallback(this))
            .workerPoolSize(1)
            .localAddress(new InetSocketAddress(host, port))
            .build()
          forwardServer.start()
          server = Some(forwardServer)
          logInfo(s"Fluentd server started on $host:$port.")
        case Some(_) =>
          logWarning("Fluentd receiver being asked to start more then once with out close.")
      }
    }
    logInfo("Fluentd receiver started.")
  }

  override def onStop(): Unit = {
    synchronized {
      server.foreach { x =>
        x.shutdown()
        server = None
      }
      logInfo("Fluentd receiver stopped.")
    }
  }

  override def preferredLocation: Option[String] = Some(host)
}

private[fluentd] object FluentdReceiver {

  private class SparkStreamingCallback(receiver: FluentdReceiver) extends ForwardCallback {
    override def consume(stream: EventStream): CompletableFuture[Void] = {
      val future = new CompletableFuture[Void]()
      try {
        receiver.store(stream)
        future.complete(null)
      } catch {
        case NonFatal(e) => future.completeExceptionally(e)
      }
      future
    }
  }

}
