package com.smartexpense.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.smartexpense.R

/** Paper surfaces shared by the everyday screens. */
@Composable internal fun StudioCard(shape:Shape=Tokens.card,content:@Composable ColumnScope.()->Unit) {
    Surface(Modifier.fillMaxWidth(),shape=shape,color=Tokens.paper,border=BorderStroke(.7.dp,Tokens.border.copy(alpha=.65f))) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content)
    }
}

/** Shared secondary action: a clear touch target without competing with Save/Add. */
@Composable internal fun StudioSecondaryAction(label:String,modifier:Modifier=Modifier,icon:Int?=null,expanded:Boolean?=null,onClick:()->Unit) {
    OutlinedButton(onClick,modifier.fillMaxWidth().heightIn(min=48.dp).semantics {
        if(expanded!=null) stateDescription=if(expanded) "Expanded" else "Collapsed"
    },shape=Tokens.field,contentPadding=PaddingValues(horizontal=16.dp,vertical=10.dp)) {
        if(icon!=null) {Icon(painterResource(icon),null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp))}
        Text(label,Modifier.weight(1f),textAlign=androidx.compose.ui.text.style.TextAlign.Start)
        Spacer(Modifier.width(8.dp))
        Icon(painterResource(R.drawable.ic_chevron_down),null,Modifier.size(18.dp).rotate(if(expanded==null) -90f else if(expanded) 180f else 0f))
    }
}

/** A small paper edge distinguishes saved receipts from editable cards. */
internal object ReceiptPaperShape:Shape {
    override fun createOutline(size:Size,layoutDirection:LayoutDirection,density:Density):Outline {
        val radius=with(density) {24.dp.toPx()}.coerceAtMost(minOf(size.width,size.height)/2)
        val tooth=with(density) {4.dp.toPx()}
        val path=Path().apply {
            moveTo(radius,0f);lineTo(size.width-radius,0f)
            quadraticTo(size.width,0f,size.width,radius)
            lineTo(size.width,size.height-tooth)
            var x=size.width
            while(x>0f) {lineTo((x-tooth).coerceAtLeast(0f),size.height);lineTo((x-2*tooth).coerceAtLeast(0f),size.height-tooth);x-=2*tooth}
            lineTo(0f,radius);quadraticTo(0f,0f,radius,0f);close()
        }
        return Outline.Generic(path)
    }
}

@Composable internal fun StudioSettingsButton(onClick:()->Unit) {
    FilledTonalIconButton(onClick,colors=IconButtonDefaults.filledTonalIconButtonColors(containerColor=Tokens.paper)) {
        Icon(painterResource(R.drawable.ic_more),contentDescription="Settings",modifier=Modifier.size(22.dp))
    }
}

@Composable internal fun StudioHeading(text:String) {
    Text(text,style=MaterialTheme.typography.headlineSmall,modifier=Modifier.semantics {heading()})
}

@Composable internal fun SettlementCard(title:String,amount:Long,caption:String?=null) {
    Surface(Modifier.fillMaxWidth().semantics(mergeDescendants=true) {},shape=Tokens.card,color=Tokens.secondarySurface,contentColor=Tokens.text) {
        Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(title,style=MaterialTheme.typography.bodyMedium)
            Text(editorialMoney(amount),style=MaterialTheme.typography.displaySmall)
            caption?.let {Text(it,style=MaterialTheme.typography.bodySmall)}
        }
    }
}

@Composable internal fun StudioSteps(step:Int) {
    Surface(shape=Tokens.field,color=Tokens.secondarySurface,modifier=Modifier.fillMaxWidth().semantics(mergeDescendants=true) {}) {
        AdaptivePairForSteps(step)
    }
}

@Composable private fun AdaptivePairForSteps(step:Int) {
    val large=androidx.compose.ui.platform.LocalDensity.current.fontScale>1.3f
    val content:@Composable (Int,String)->Unit={i,label->
        Row(Modifier.padding(8.dp),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
            Surface(shape=CircleShape,color=if(i==step) Tokens.accent else Tokens.paper,contentColor=if(i==step) MaterialTheme.colorScheme.onPrimary else Tokens.muted) {
                Box(Modifier.size(26.dp),contentAlignment=Alignment.Center) {Text("${i+1}",style=MaterialTheme.typography.labelMedium)}
            }
            Text(label,style=MaterialTheme.typography.labelMedium,color=if(i==step) Tokens.text else Tokens.muted)
        }
    }
    if(large) Column {listOf("Bill","Payments","Review").forEachIndexed {i,label->content(i,label)}}
    else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {listOf("Bill","Payments","Review").forEachIndexed {i,label->content(i,label)}}
}
