package com.smartexpense.data

import com.smartexpense.domain.*
import org.json.*

object PlanCodec {
    fun favourite(value:FavouriteExpense)=PlanEntity("favourite:${value.id}","favourite",JSONObject().put("id",value.id).put("description",value.description).put("amount",value.amount).put("account",value.accountId).put("category",value.categoryId?:JSONObject.NULL).toString())
    fun favourite(row:PlanEntity):FavouriteExpense {val j=JSONObject(row.json);return FavouriteExpense(j.getString("id"),j.getString("description"),integer(j.get("amount")),j.getString("account"),if(j.isNull("category")) null else j.getString("category"))}
    private fun integer(value:Any):Long {
        require(value is Int || value is Long) {"Money must be stored as whole paise"}
        return (value as Number).toLong()
    }
    fun group(value:GroupDefault)=PlanEntity("group:${value.groupId}","group",JSONObject().put("group",value.groupId).put("method",value.method).put("values",JSONObject(value.values)).toString())
    fun group(row:PlanEntity):GroupDefault {val j=JSONObject(row.json);val values=j.getJSONObject("values");return GroupDefault(j.getString("group"),j.getString("method"),values.keys().asSequence().associateWith {values.getString(it)})}
    fun template(value:RecurringTemplate)=PlanEntity("template:${value.id}","template",JSONObject().put("id",value.id).put("kind",value.kind.name).put("amount",value.amount).put("description",value.description).put("account",value.accountId).put("first",value.firstDate).put("frequency",value.frequency).toString())
    fun template(row:PlanEntity):RecurringTemplate {val j=JSONObject(row.json);return RecurringTemplate(j.getString("id"),Kind.valueOf(j.getString("kind")),integer(j.get("amount")),j.getString("description"),j.getString("account"),j.getString("first"),j.getString("frequency"))}
    fun goal(value:SavingsGoal)=PlanEntity("goal:${value.id}","goal",JSONObject().put("id",value.id).put("name",value.name).put("target",value.target).put("contributions",JSONArray().apply {value.contributions.forEach {put(JSONArray().put(it.id).put(it.date).put(it.amount))}}).toString())
    fun goal(row:PlanEntity):SavingsGoal {val j=JSONObject(row.json);val array=j.getJSONArray("contributions");return SavingsGoal(j.getString("id"),j.getString("name"),integer(j.get("target")),(0 until array.length()).map {val r=array.getJSONArray(it);require(r.length()==3);GoalContribution(r.getString(0),r.getString(1),integer(r.get(2)))})}
    fun category(value:CategoryRule)=PlanEntity("category:${value.id}","category",JSONObject().put("id",value.id).put("merchant",value.merchant).put("category",value.categoryId).toString())
    fun category(row:PlanEntity):CategoryRule {val j=JSONObject(row.json);return CategoryRule(j.getString("id"),j.getString("merchant"),j.getString("category"))}
    fun reminder(value:ReminderSettings)=PlanEntity("settings:reminder","reminder",JSONObject().put("enabled",value.enabled).put("hour",value.hour).put("minute",value.minute).put("lastDate",value.lastNotifiedDate).toString())
    fun reminder(row:PlanEntity):ReminderSettings {
        val j=JSONObject(row.json);val enabled=j.get("enabled");require(enabled is Boolean)
        val hour=integer(j.get("hour"));val minute=integer(j.get("minute"));require(hour in 0..23 && minute in 0..59)
        return ReminderSettings(enabled,hour.toInt(),minute.toInt(),j.getString("lastDate"))
    }
    fun validate(row:PlanEntity,groups:List<Group>,accountIds:Set<String>,categoryIds:Set<String>) {
        require(row.json.length<=10000)
        when(row.kind) {
            "favourite"->{val value=favourite(row);require(row.key=="favourite:${value.id}" && value.accountId in accountIds && (value.categoryId==null || value.categoryId in categoryIds))}
            "reminder"->{require(row.key=="settings:reminder");reminder(row)}
            "group"->{val value=group(row);require(row.key=="group:${value.groupId}");value.validate((listOf("self")+groups.single {it.id==value.groupId}.members).distinct())}
            "template"->{val value=template(row);require(row.key=="template:${value.id}" && value.accountId in accountIds)}
            "goal"->{val value=goal(row);require(row.key=="goal:${value.id}" && value.allocated<=value.target)}
            "category"->{val value=category(row);require(row.key=="category:${value.id}" && value.merchant.isNotBlank() && value.merchant.length<=100 && value.categoryId in categoryIds)}
            else->throw IllegalArgumentException("Unknown planning record")
        }
    }
}
