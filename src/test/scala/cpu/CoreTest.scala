package cpu

import chisel3._
import chiseltest._
import chiseltest.experimental.TestOptionBuilder.ChiselScalatestOptionBuilder
import chiseltest.internal.WriteVcdAnnotation
import cpu.ifu.BPU
import org.scalatest.FlatSpec
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}
import cpu.exu.Rob

class CoreTest extends FlatSpec with ChiselScalatestTester {
  //(new ChiselStage).execute(Array.empty, Seq(ChiselGeneratorAnnotation(() => new Sram)))
  "Core" should "run" in {
    test(new Core).withAnnotations(Seq(WriteVcdAnnotation)){
      i=>i.clock.step(100)
    }
  }
}

