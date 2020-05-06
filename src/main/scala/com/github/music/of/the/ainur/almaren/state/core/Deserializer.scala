package com.github.music.of.the.ainur.almaren.state.core

import com.github.music.of.the.ainur.almaren.State
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.types.{DataType, StructType}

import scala.language.implicitConversions
import com.github.music.of.the.ainur.almaren.Almaren
import com.github.music.of.the.ainur.almaren.util.Constants
import org.apache.spark.sql.Dataset

abstract class Deserializer() extends State {
  override def executor(df: DataFrame): DataFrame = deserializer(df)
  def deserializer(df: DataFrame): DataFrame
  implicit def string2Schema(schema: String): DataType =
    StructType.fromDDL(schema)
  
}

case class AvroDeserializer(columnName: String,schema: String,params:Map[String,String]) extends Deserializer {
  import org.apache.spark.sql.avro._
  import org.apache.spark.sql.functions._
  override def deserializer(df: DataFrame): DataFrame = {
    logger.info(s"columnName:{$columnName}, schema:{$schema}")
    df.withColumn(columnName,from_avro(col(columnName),schema))
      .select("*",columnName.concat(".*")).drop(columnName)


  }
}

case class JsonDeserializer(columnName: String,schema: Option[String],params:Map[String,String]) extends Deserializer {
  import org.apache.spark.sql.functions._
  override def deserializer(df: DataFrame): DataFrame = {
    import df.sparkSession.implicits._
    logger.info(s"columnName:{$columnName}, schema:{$schema}")
    df.withColumn(columnName,
      from_json(col(columnName),
        schema.getOrElse(getSchemaDDL(df.selectExpr(columnName).as[(String)]))))
      .select("*",columnName.concat(".*"))
      .drop(columnName)
  }
  private def getSchemaDDL(df: Dataset[String]): String =
    Almaren.spark.getOrCreate().read.json(df.sample(Constants.sampleDeserializer)).schema.toDDL
}

case class XMLDeserializer(columnName: String,params:Map[String,String]) extends Deserializer {
  import com.databricks.spark.xml.XmlReader
  override def deserializer(df: DataFrame): DataFrame = {
    logger.info(s"columnName:{$columnName}")
    new XmlReader().xmlRdd(df.sparkSession,df.select(columnName).rdd.map(r => r(0).asInstanceOf[String])).toDF
  }
}
