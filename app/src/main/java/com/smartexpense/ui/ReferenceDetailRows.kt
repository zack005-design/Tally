package com.smartexpense.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.smartexpense.R

/** Plain leading symbols match the compact record forms; values remain fully readable. */
@Composable internal fun ReferenceDetailRow(label:String,icon:Int,value:String,onClick:(()->Unit)?=null) {
    val action=if(onClick!=null) Modifier.clickable(role=Role.Button,onClick=onClick) else Modifier.semantics(mergeDescendants=true) {}
    Row(action.fillMaxWidth().heightIn(min=48.dp).padding(horizontal=14.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Icon(painterResource(icon),null,Modifier.size(20.dp),tint=Tokens.muted)
        if(LocalDensity.current.fontScale>1.3f) Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text(label,color=Tokens.muted,style=MaterialTheme.typography.bodyMedium)
            Text(value,style=MaterialTheme.typography.bodyLarge)
        } else {
            Text(label,Modifier.weight(1f),color=Tokens.muted,style=MaterialTheme.typography.bodyMedium)
            Text(value,Modifier.weight(1.3f),style=MaterialTheme.typography.bodyMedium,textAlign=TextAlign.End)
        }
        if(onClick!=null) Text("›",color=Tokens.muted)
    }
}

@Composable internal fun ReferenceNotice(title:String,subtitle:String,success:Boolean=false) {
    Surface(Modifier.fillMaxWidth(),shape=Tokens.card,color=Tokens.secondarySurface) {
        Row(Modifier.padding(16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.Top) {
            if(success) Surface(shape=androidx.compose.foundation.shape.CircleShape,color=Tokens.accent,modifier=Modifier.size(30.dp)) {
                Box(contentAlignment=Alignment.Center) {Icon(painterResource(R.drawable.ic_check_outline),null,Modifier.size(19.dp),tint=MaterialTheme.colorScheme.onPrimary)}
            } else Icon(painterResource(R.drawable.ic_info),null,Modifier.size(22.dp),tint=Tokens.muted)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(title,style=MaterialTheme.typography.titleSmall,color=if(success) Tokens.accent else Tokens.text)
                Text(subtitle,style=MaterialTheme.typography.bodySmall,color=Tokens.muted)
            }
        }
    }
}
