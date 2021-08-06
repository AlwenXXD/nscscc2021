//package cpu
//
//import chisel3._
//import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}
//import chiseltest._
//import chiseltest.experimental.TestOptionBuilder.ChiselScalatestOptionBuilder
//import chiseltest.internal.WriteVcdAnnotation
//import cpu.ifu.BPU
//import org.scalatest.FlatSpec
//
//class TestTemplate extends FlatSpec with ChiselScalatestTester {
//(new ChiselStage).execute(Array.empty, Seq(ChiselGeneratorAnnotation(() => new Sram)))
//  "" should "" in {
//    test(new TestTemplate).withAnnotations(Seq(WriteVcdAnnotation)){
//      i=>
//    }
//  }
//}
