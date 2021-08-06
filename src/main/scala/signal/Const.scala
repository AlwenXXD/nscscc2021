package signal

import chisel3.util._
import chisel3._

object Const {
  val X           = BitPat("b?")
  val N           = BitPat("b0")
  val Y           = BitPat("b1")
  val FETCH_WIDTH = 8

  val ICACHE_DEPTH = 128
  val ICACHE_OFFSET_WIDTH = log2Up(FETCH_WIDTH*4)
  val ICACHE_INST_WIDTH = log2Up(FETCH_WIDTH)
  val ICACHE_INDEX_WIDTH = log2Up(ICACHE_DEPTH)
  val ICACHE_TAG_WIDTH = 32 - ICACHE_INDEX_WIDTH - ICACHE_OFFSET_WIDTH

  val FETCH_BUFFER_DEPTH = 17

  val COMMIT_WIDTH = 2
  val ISSUE_WIDTH = 2
  val WRITE_BUFFER_DEPTH = 4
  val DISPATCH_WIDTH = 5
  val SRAM_DELAY = 1

  val ROB_DEPTH = 8
  val ROB_IDX_WIDTH = log2Up(ROB_DEPTH)
  val UART_BUFFER_DEPTH = 8

}
