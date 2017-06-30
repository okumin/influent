package sample

import org.apache.spark.SparkConf
import org.apache.spark.storage.StorageLevel
import org.apache.spark.streaming.fluentd.FluentdUtils
import org.apache.spark.streaming.{Milliseconds, StreamingContext}

object EventCounter {
  def main(args: Array[String]): Unit = {
    val host = args(0)
    val port = args(1).toInt

    val sparkConf = new SparkConf().setAppName("fluentd-event-counter")
    val ssc = new StreamingContext(sparkConf, Milliseconds(5000))

    val stream = FluentdUtils.createStream(ssc, host, port, StorageLevel.MEMORY_ONLY)

    stream.map { eventStream =>
      eventStream.getTag.getName -> eventStream.getEntries.size().toLong
    }.reduceByKey(_ + _).print()

    ssc.start()
    ssc.awaitTerminationOrTimeout(300 * 1000)
  }
}
