package cpu.exu

import chisel3._
import chiseltest._
import chiseltest.experimental.TestOptionBuilder.ChiselScalatestOptionBuilder
import chiseltest.internal.WriteVcdAnnotation
import cpu.ifu.BPU
import org.scalatest.FlatSpec

class ExuTest extends FlatSpec with ChiselScalatestTester {
  "ExuTest" should "run" in {
    test(new Exu).withAnnotations(Seq(WriteVcdAnnotation)){
      i=>
    }
  }
}
