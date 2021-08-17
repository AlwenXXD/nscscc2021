package cpu.exu

import chisel3.{Vec, _}
import chisel3.util._
import chisel3.experimental._
import cpu.ifu.{BranchInfo, FBInstBank}
import instructions.MIPS32._
import signal.Const._
import signal._

import scala.:+

class RobInfo extends Bundle {
  val is_valid        = Bool()
  val busy            = Bool()
  val uop             = uOP()
  val inst_addr       = UInt(32.W)
  val commit_addr     = UInt(5.W)
  val commit_data     = UInt(32.W)
  val commit_target   = TargetSel()
  val commit_ready    = Bool()
  val except_type     = ExceptSel()
  val is_branch       = Bool()
  val is_delay        = Bool()
  val predict_taken   = Bool()
  val is_taken        = Bool()
  val predict_miss    = Bool()
  val gh_info         = UInt(4.W)
  val imm_data        = UInt(32.W)
  val flush_on_commit = Bool()

  def InitRob(): Unit ={
    is_valid:= false.B
    busy:= false.B
    uop:= uOP.NOP
    inst_addr:= 0.U(32.W)
    commit_addr:= 0.U(5.W)
    commit_data:= 0.U(32.W)
    commit_target:= TargetSel.none
    commit_ready:= false.B
    except_type:= ExceptSel.is_null
    is_branch:= false.B
    is_delay:= false.B
    predict_taken:= false.B
    is_taken:= false.B
    predict_miss:= false.B
    gh_info:= 0.U(4.W)
    imm_data:= 0.U(32.W)
    flush_on_commit:= false.B
  }
}

class RobAllocateInfo extends Bundle {
  val rob_idx         = UInt(ROB_IDX_WIDTH.W)
  val inst_valid      = Bool()
  val inst_addr       = UInt(32.W)
  val commit_addr     = UInt(32.W)
  val commit_target   = TargetSel()
  val except_type     = ExceptSel()
  val is_branch       = Bool()
  val is_delay        = Bool()
  val gh_info         = UInt(4.W)
  val flush_on_commit = Bool()
  def init(): Unit ={
    rob_idx         := 0.U(ROB_IDX_WIDTH.W)
    inst_valid      := false.B
    inst_addr       := 0.U(32.W)
    commit_addr     := 0.U(32.W)
    commit_target   := TargetSel.none
    except_type     := ExceptSel.is_null
    is_branch       := false.B
    is_delay        := false.B
    gh_info         := 0.U(4.W)
    flush_on_commit := false.B
  }
}

class RobAllocateResp extends Bundle {
  val rob_idx = Vec(ISSUE_WIDTH, UInt(ROB_IDX_WIDTH.W))
  val enq_valid_mask = Vec(ISSUE_WIDTH, Bool())
}

class RobAllocateIO extends Bundle {
  val allocate_req = Flipped(Valid(Vec(ISSUE_WIDTH, new Bool())))
  val allocate_info = Flipped(Valid(Vec(ISSUE_WIDTH, new RobAllocateInfo())))
  val allocate_resp = Valid(new RobAllocateResp)
}

class WriteBackInfo extends Bundle {
  val rob_idx      = UInt(ROB_IDX_WIDTH.W)
  val data         = UInt(32.W)
  val target_addr  = UInt(32.W)
  val is_taken     = Bool()
  val predict_miss = Bool()
  def init(): Unit ={
    rob_idx      := 0.U(ROB_IDX_WIDTH.W)
    data         := 0.U(32.W)
    target_addr  := 0.U(32.W)
    is_taken     := false.B
    predict_miss := false.B
  }
}

class DispatchInfo extends Bundle {
  val uop           = uOP()
  val need_imm      = Bool()
  val rob_idx       = UInt(ROB_IDX_WIDTH.W)
  val inst_addr     = UInt(32.W)
  val op1_data      = UInt(32.W)
  val op2_data      = UInt(32.W)
  val imm_data      = UInt(32.W)
  val predict_taken = Bool()
  def init(): Unit ={
    uop           := uOP.NOP
    need_imm      := false.B
    rob_idx       := 0.U(ROB_IDX_WIDTH.W)
    inst_addr     := 0.U(32.W)
    op1_data      := 0.U(32.W)
    op2_data      := 0.U(32.W)
    imm_data      := 0.U(32.W)
    predict_taken := false.B
  }
}

class RobIO extends Bundle {
  val rob_allocate    = new RobAllocateIO
  val rob_read        = Vec(ISSUE_WIDTH, Flipped(new RobReadIO()))
  val wb_info_i       = Vec(5, Flipped(Valid(new WriteBackInfo)))
  val rob_commit      = Vec(COMMIT_WIDTH, Valid(new RobCommitInfo))
  val branch_info     = Valid(new BranchInfo)
  val need_flush      = Output(Bool())
  val need_stop      = Input(Bool())

}

class Rob extends Module {
  def leftRotate(i: UInt, n: Int): UInt = {
    val w = i.getWidth
    if (n > 0) {
      Cat(i(w - n - 1, 0), i(w - 1, w - n))
    }
    else {
      i
    }
  }

  val io             = IO(new RobIO)
  val rob_info       = Reg(Vec(ROB_DEPTH, new RobInfo))
  val head           = RegInit(1.U(ROB_DEPTH.W))
  val head_next = Wire(UInt(ROB_DEPTH.W))
  val tail           = RegInit(1.U(ROB_DEPTH.W))
  val maybe_full     = RegInit(false.B)
  val hit_head       = head === tail
  val is_full        = hit_head && maybe_full
  val is_empty       = hit_head && !maybe_full
  val enq_idxs       = Wire(Vec(ISSUE_WIDTH, UInt(ROB_DEPTH.W)))
  val might_hit_head_mask = Wire(Vec(ISSUE_WIDTH, Bool()))
  val might_hit_head =  might_hit_head_mask.reduce(_ | _)
  val inst_valid_mask = io.rob_allocate.allocate_req.bits.map(_&io.rob_allocate.allocate_req.valid)
  val waiting_delay = RegInit(false.B)
  val need_flush = RegInit(false.B)
  var next_tail        = tail
  var might_hit = WireInit(false.B)
  for (i <- 0 until ISSUE_WIDTH) {
    enq_idxs(i) := next_tail
    might_hit = might_hit || (next_tail===head && (!is_empty))
    might_hit_head_mask(i):= might_hit
    next_tail = Mux(inst_valid_mask(i)&&(!might_hit), leftRotate(next_tail, 1), next_tail)
  }
  val enq_valid_mask = might_hit_head_mask.zip(inst_valid_mask).map(i=>(!i._1)&i._2& !io.need_stop)
  val has_valid = enq_valid_mask.reduce(_ | _)
  val do_enq    =  has_valid

  tail := next_tail

  io.rob_allocate.allocate_resp.bits.rob_idx := enq_idxs.map(OHToUInt(_))
  io.rob_allocate.allocate_resp.bits.enq_valid_mask := VecInit(enq_valid_mask)
  io.rob_allocate.allocate_resp.valid := has_valid



  //enq logic
  for (j <- 0 until ISSUE_WIDTH) {
    val rob_idx = io.rob_allocate.allocate_info.bits(j).rob_idx
    when(io.rob_allocate.allocate_info.valid&&io.rob_allocate.allocate_info.bits(j).inst_valid && !need_flush && !io.need_stop) {
      rob_info(rob_idx).commit_ready := false.B
      rob_info(rob_idx).busy := false.B
      rob_info(rob_idx).is_valid := true.B
      rob_info(rob_idx).predict_miss := false.B
      rob_info(rob_idx).is_taken := false.B
      rob_info(rob_idx).inst_addr := io.rob_allocate.allocate_info.bits(j).inst_addr
      rob_info(rob_idx).commit_addr := io.rob_allocate.allocate_info.bits(j).commit_addr
      rob_info(rob_idx).commit_target := io.rob_allocate.allocate_info.bits(j).commit_target
      rob_info(rob_idx).except_type := io.rob_allocate.allocate_info.bits(j).except_type
      rob_info(rob_idx).is_branch := io.rob_allocate.allocate_info.bits(j).is_branch
      rob_info(rob_idx).is_delay := io.rob_allocate.allocate_info.bits(j).is_delay
      rob_info(rob_idx).gh_info := io.rob_allocate.allocate_info.bits(j).gh_info
      rob_info(rob_idx).flush_on_commit := io.rob_allocate.allocate_info.bits(j).flush_on_commit
    }
  }


  //write-back logic
  for (j <- 0 until 5) {
    val des_rob = io.wb_info_i(j).bits.rob_idx
    when(io.wb_info_i(j).valid&&rob_info(des_rob).is_valid&& !need_flush) {
      rob_info(des_rob).commit_data := io.wb_info_i(j).bits.data
      rob_info(des_rob).commit_ready := true.B
      rob_info(des_rob).busy := false.B
      rob_info(des_rob).is_taken := io.wb_info_i(j).bits.is_taken
      rob_info(des_rob).predict_miss := io.wb_info_i(j).bits.predict_miss
      rob_info(des_rob).imm_data := io.wb_info_i(j).bits.target_addr

    }
  }

  //deq logic
  val deq_idxs        = VecInit((0 until COMMIT_WIDTH+1).map(i=> leftRotate(head, i)))
  val deq_robs        = VecInit(deq_idxs.dropRight(1).map(OHToUInt(_)))
  val commit_ready_mask = deq_robs.map(i=>rob_info(i).commit_ready&&rob_info(i).is_valid)
  val commit_wait_mask = commit_ready_mask.map(!_)
  val need_flush_mask = VecInit(deq_robs.map(i=>rob_info(i).predict_miss||rob_info(i).flush_on_commit))
  val deq_wait_mask = commit_wait_mask.zip(false.B+:need_flush_mask.dropRight(1)).scanLeft(false.B){case (p,(wait,flush))=>p||flush || wait}.drop(1) :+ false.B
   head_next       := deq_idxs(PriorityEncoder(deq_wait_mask))
  val deq_ready_mask = VecInit(deq_wait_mask.map(!_))
  val flush_idx = PriorityEncoder(need_flush_mask)
  val flush_rob = deq_robs(flush_idx)
  val flush           = need_flush_mask(flush_idx) && deq_ready_mask(flush_idx)
  val do_deq          = deq_ready_mask(0)

  //commit logic
  val rob_commit       = Reg(Vec(COMMIT_WIDTH, new RobCommitInfo))
  val rob_commit_valid = RegInit(VecInit(Seq.fill(COMMIT_WIDTH)(false.B)))
  for (j <- 0 until COMMIT_WIDTH) {
    val rob_idx = deq_robs(j)
    rob_commit_valid(j) := deq_ready_mask(j)
    rob_commit(j).commit_data := rob_info(rob_idx).commit_data
    rob_commit(j).des_rob := rob_idx
    rob_commit(j).commit_addr := rob_info(rob_idx).commit_addr
    when(deq_ready_mask(j)&& !io.need_stop){
      rob_info(rob_idx).InitRob()
    }
    io.rob_commit(j).bits:=rob_commit(j)
    io.rob_commit(j).valid:=rob_commit_valid(j)
  }





  val branch_info       = Reg(new BranchInfo)
  val branch_info_valid = RegInit(false.B)
  branch_info_valid := false.B
  branch_info.target_addr := rob_info(flush_rob).imm_data
  branch_info.inst_addr := rob_info(flush_rob).inst_addr
  branch_info.gh_update := rob_info(flush_rob).gh_info
  branch_info.is_branch := rob_info(flush_rob).is_branch
  branch_info.is_taken := rob_info(flush_rob).is_taken
  branch_info.predict_miss := rob_info(flush_rob).predict_miss

  io.branch_info.bits:=branch_info
  io.branch_info.valid:=branch_info_valid

  val op1_in_commit = Wire(Vec(ISSUE_WIDTH,Bool()))
  val op2_in_commit = Wire(Vec(ISSUE_WIDTH,Bool()))
  val op1_commitData = Wire(Vec(ISSUE_WIDTH,UInt(32.W)))
  val op2_commitData = Wire(Vec(ISSUE_WIDTH,UInt(32.W)))
  for (i<-0 until ISSUE_WIDTH){
    op1_in_commit(i):= rob_commit.zip(rob_commit_valid).map{case (commit,valid)=> commit.des_rob === io.rob_read(i).op1_idx && valid}.reduce(_|_)
    op2_in_commit(i):= rob_commit.zip(rob_commit_valid).map{case (commit,valid)=> commit.des_rob === io.rob_read(i).op2_idx && valid}.reduce(_|_)
    op1_commitData(i) := rob_commit.zip(rob_commit_valid).map{case (commit,valid)=> Fill(32,commit.des_rob === io.rob_read(i).op1_idx && valid) & commit.commit_data}.reduce(_|_)
    op2_commitData(i) := rob_commit.zip(rob_commit_valid).map{case (commit,valid)=> Fill(32,commit.des_rob === io.rob_read(i).op2_idx && valid) & commit.commit_data}.reduce(_|_)
  }
  for (i <- 0 until ISSUE_WIDTH){
    val op1_idx = io.rob_read(i).op1_idx
    val op2_idx = io.rob_read(i).op2_idx
    io.rob_read(i).op1_data:= Mux(op1_in_commit(i),op1_commitData(i),rob_info(op1_idx).commit_data)
    io.rob_read(i).op1_ready:=rob_info(op1_idx).commit_ready || op1_in_commit(i)
    io.rob_read(i).op2_data:= Mux(op2_in_commit(i),op2_commitData(i),rob_info(op2_idx).commit_data)
    io.rob_read(i).op2_ready:=rob_info(op2_idx).commit_ready || op2_in_commit(i)
  }



  head := head_next
  when(do_deq) {
    maybe_full := false.B
  }.elsewhen(do_enq) {
    maybe_full := true.B
  }
  val delay_mask = VecInit(rob_info.map(i=>i.is_delay))
  val has_delay = (delay_mask.asUInt()&head).orR()




  when(flush) {
    waiting_delay := true.B
//    for (i <- 0 until 16) {
//      when(!(rob_info(i).is_delay && head(i))){
//        rob_info(i).InitRob()
//      }.otherwise{
//        rob_info(i).busy:=false.B
//      }
//    }
//    when(has_delay){
//      tail := leftRotate(head,1)
//      waiting_delay := true.B
//    }.otherwise{
//      tail := head
//    }
//    maybe_full := false.B
  }

  when(waiting_delay){
    (1 until COMMIT_WIDTH).foreach(i=>rob_commit_valid(i):=false.B)
    branch_info:=branch_info
    when(deq_ready_mask(0)){
      waiting_delay:=false.B
      need_flush:=true.B
      branch_info_valid := true.B
    }.otherwise{
      need_flush:=false.B
    }
  }.otherwise{
    need_flush:=false.B
  }

  io.need_flush:=need_flush

  when(io.need_stop){
    head:=head
    tail:=tail
    maybe_full:=maybe_full
    waiting_delay:=waiting_delay
    need_flush :=need_flush
    rob_commit:= rob_commit
    rob_commit_valid:= rob_commit_valid
    branch_info:=branch_info
    branch_info_valid:=branch_info_valid
  }

  when(need_flush){
    for (i <- 0 until ROB_DEPTH) {
      rob_info(i).InitRob()
    }
    branch_info.init()
    branch_info_valid:=false.B

    rob_commit.foreach(i => {
      i.init()
    })
    rob_commit_valid.foreach(i=>{
      i:=false.B
    })
    tail := leftRotate(head,1)
    head := leftRotate(head,1)
    maybe_full := false.B
    waiting_delay:=false.B
  }

  when(reset.asBool()) {
    rob_info.foreach(i => {
      i.InitRob()
    })
    rob_commit.foreach(i => {
      i.init()
    })
    branch_info.init()
  }
}
