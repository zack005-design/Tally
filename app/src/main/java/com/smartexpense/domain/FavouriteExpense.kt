package com.smartexpense.domain

data class FavouriteExpense(val id:String,val description:String,val amount:Long,val accountId:String,val categoryId:String?) {
    init {
        require(id.isNotBlank() && id.length<=100)
        require(description.isNotBlank() && description.length<=200) {"Enter a description under 200 characters"}
        require(amount>0) {"Enter a positive amount"}
        require(accountId.isNotBlank()) {"Choose an account"}
    }
    fun draft(date:Long)=EntryDraft(amount=amount,description=description,accountId=accountId,categoryId=categoryId,date=date)
}
