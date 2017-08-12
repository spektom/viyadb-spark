package com.github.viyadb.spark.processing

import com.github.viyadb.spark.Configs.JobConf
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.functions._

/**
  * Utilities for rolling up data set according to table definition
  */
case class Aggregator(config: JobConf) extends Processor(config) {

  override def process(df: DataFrame): DataFrame = {
    val aggCols = (config.table.timeColumn.map(_.name).toSeq
      ++ config.table.dimensions.map(_.name)
      ++ config.table.metrics.filter(_.`type` == "bitset").map(_.name))
      .distinct.map(col(_))

    val aggExprs = config.table.metrics.filter(_.`type` != "bitset").map { metric =>
      (metric.`type` match {
        case "count" => sum(metric.name)
        case other => other.split("_")(1) match {
          case "sum" => sum(metric.name)
          case "min" => min(metric.name)
          case "max" => max(metric.name)
        }
      }).as(metric.name)
    }

    val addCounts = config.table.metrics.filter(_.`type` == "count")
      .map(metric => (df: DataFrame) => if (df.columns.contains(metric.name)) df else df.withColumn(metric.name, lit(1)))
      .reduceLeft(_ compose _)

    df.transform(addCounts).groupBy(aggCols: _*).agg(aggExprs.head, aggExprs.tail: _*)
  }
}