package io

import chisel3._
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}
import chiseltest._
import chiseltest.experimental.TestOptionBuilder.ChiselScalatestOptionBuilder
import chiseltest.internal.WriteVcdAnnotation
import cpu.ifu.BPU
import org.scalatest.FlatSpec

class UartTest extends FlatSpec with ChiselScalatestTester {
(new ChiselStage).execute(Array.empty, Seq(ChiselGeneratorAnnotation(() => new Uart)))
  "Uart" should "run" in {
    test(new Uart).withAnnotations(Seq(WriteVcdAnnotation)){
      i=>
    }
  }
}
