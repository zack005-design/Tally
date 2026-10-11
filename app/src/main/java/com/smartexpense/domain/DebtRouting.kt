package com.smartexpense.domain

/** Routes an explicitly recorded settlement through original debts, retaining each allocation. */
object DebtRouting {
    private data class Edge(val to:String,val reverse:Int,var remaining:Long,val debtId:String?,val original:Long)
    private data class Route(val amount:Long,val allocations:Map<String,Long>)
    private fun route(from:String,to:String,debts:List<Debt>,limit:Long):Route {
        require(from!=to && limit>=0)
        val graph=linkedMapOf<String,MutableList<Edge>>()
        debts.filter {it.outstanding>0}.sortedWith(compareBy<Debt> {it.date}.thenBy {it.id}).forEach {debt->
            val outgoing=graph.getOrPut(debt.from) {mutableListOf()};val incoming=graph.getOrPut(debt.to) {mutableListOf()}
            outgoing+=Edge(debt.to,incoming.size,debt.outstanding,debt.id,debt.outstanding);incoming+=Edge(debt.from,outgoing.lastIndex,0,null,0)
        }
        var sent=0L
        while(sent<limit) {
            val parent=mutableMapOf<String,Pair<String,Int>>();val seen=mutableSetOf(from);val queue=java.util.ArrayDeque<String>();queue.add(from)
            while(queue.isNotEmpty() && to !in seen) {val node=queue.removeFirst();graph[node].orEmpty().forEachIndexed {index,edge->if(edge.remaining>0 && seen.add(edge.to)) {parent[edge.to]=node to index;queue.add(edge.to)}}}
            if(to !in seen) break
            var part=limit-sent;var node=to
            while(node!=from) {val (previous,index)=parent.getValue(node);part=minOf(part,graph.getValue(previous)[index].remaining);node=previous}
            node=to
            while(node!=from) {val (previous,index)=parent.getValue(node);val edge=graph.getValue(previous)[index];edge.remaining-=part;graph.getValue(node)[edge.reverse].remaining=Math.addExact(graph.getValue(node)[edge.reverse].remaining,part);node=previous}
            sent=Math.addExact(sent,part)
        }
        val allocations=graph.values.flatten().filter {it.debtId!=null && it.original>it.remaining}.associate {it.debtId!! to it.original-it.remaining}
        return Route(sent,allocations)
    }
    fun capacity(from:String,to:String,debts:List<Debt>):Long=if(from==to || from.isBlank() || to.isBlank()) 0 else route(from,to,debts,Long.MAX_VALUE).amount
    fun allocate(amount:Long,from:String,to:String,debts:List<Debt>):Map<String,Long> {require(amount>0);val route=route(from,to,debts,amount);require(route.amount==amount) {"Payment exceeds the original debts that can be settled in this direction"};return route.allocations}
    fun suggestions(debts:List<Debt>):List<SuggestedSettlement> {
        var remaining=debts
        val net=linkedMapOf<String,Long>()
        debts.forEach {net[it.from]=Math.subtractExact(net[it.from]?:0,it.outstanding);net[it.to]=Math.addExact(net[it.to]?:0,it.outstanding)}
        val result=mutableListOf<SuggestedSettlement>()
        net.keys.toList().filter {net.getValue(it)<0}.forEach {from->
            net.keys.toList().filter {net.getValue(it)>0}.forEach {to->
                val amount=minOf(-net.getValue(from),net.getValue(to),capacity(from,to,remaining))
                if(amount>0) {val allocation=allocate(amount,from,to,remaining);remaining=remaining.map {it.copy(outstanding=it.outstanding-(allocation[it.id]?:0))};net[from]=net.getValue(from)+amount;net[to]=net.getValue(to)-amount;result+=SuggestedSettlement(from,to,amount)}
            }
        }
        check(net.values.all {it==0L}) {"Could not reconcile debt paths"}
        return result
    }
}
