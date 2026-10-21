package com.smartexpense.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** A labelled amount field; display grouping never changes the editable paise input. */
@Composable internal fun ReferenceAmountField(label:String,value:String,onChange:(String)->Unit,compact:Boolean=false) {
    val focus=LocalFocusManager.current
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        if(!compact) Text(label,style=MaterialTheme.typography.bodyMedium,color=Tokens.muted)
        Surface(shape=Tokens.field,color=Tokens.paper,border=androidx.compose.foundation.BorderStroke(1.dp,Tokens.border)) {
            BasicTextField(value,{onChange(it.replace(",", "").removePrefix("₹"))},
                Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=14.dp).heightIn(min=32.dp).semantics {contentDescription=label},
                textStyle=(if(compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineMedium).copy(color=Tokens.text),singleLine=true,
                cursorBrush=SolidColor(Tokens.accent),visualTransformation=RupeeMoneyTransformation,
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal,imeAction=ImeAction.Done),
                keyboardActions=KeyboardActions(onDone={focus.clearFocus()}))
        }
    }
}
