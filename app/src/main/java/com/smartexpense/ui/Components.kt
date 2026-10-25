package com.smartexpense.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import com.smartexpense.R
import com.smartexpense.domain.Money

val LocalEditorialBack = staticCompositionLocalOf<(() -> Unit)?> { null }

@Composable fun Page(title:String, subtitle:String="", actions:@Composable RowScope.()->Unit={}, onBack:(()->Unit)?=null, onSubtitle:(()->Unit)?=null, largeTitle:Boolean=false, gap:androidx.compose.ui.unit.Dp=Tokens.sectionGap, content:LazyListScope.()->Unit) {
    val back = onBack ?: LocalEditorialBack.current
    val centeredTitle=title in setOf("Record income","Record refund","Describe entry","Foreign bill","Transaction") && LocalDensity.current.fontScale<=1.3f
    val parent = when(title) {
        "App lock","Reminders","Backup & restore","GPay reset schedule" -> "Settings"
        "Add money","Transfer money","Balance check" -> "Accounts"
        else -> null
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxSize().imePadding()) {
                Column(Modifier.fillMaxWidth().background(Tokens.background)
                    .padding(start=Tokens.pageInset,end=Tokens.pageInset,top=12.dp,bottom=4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if(back!=null && (parent!=null || largeTitle)) {
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                            TextButton(back,Modifier.semantics {contentDescription="Back"},contentPadding=PaddingValues(0.dp)) {
                                Icon(painterResource(R.drawable.ic_chevron_down),null,Modifier.size(20.dp).rotate(90f))
                                Text(parent?:"Back",style=MaterialTheme.typography.bodyLarge)
                            }
                            Spacer(Modifier.weight(1f))
                            Row(horizontalArrangement=Arrangement.End,verticalAlignment=Alignment.CenterVertically,content=actions)
                        }
                        Text(title,Modifier.semantics {heading()},style=if(largeTitle) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium)
                    } else if(back!=null && centeredTitle) {
                        Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically) {
                            TextButton(back,Modifier.width(72.dp).semantics {contentDescription="Back"},contentPadding=PaddingValues(0.dp)) {
                                if(title in setOf("Record income","Record refund")) Text("Cancel",style=MaterialTheme.typography.bodyLarge)
                                else {Icon(painterResource(R.drawable.ic_chevron_down),null,Modifier.size(18.dp).rotate(90f));Text("Back",style=MaterialTheme.typography.bodyLarge)}
                            }
                            Text(title,Modifier.weight(1f).semantics {heading()},style=MaterialTheme.typography.titleMedium,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                            Row(Modifier.width(72.dp),horizontalArrangement=Arrangement.End,verticalAlignment=Alignment.CenterVertically,content=actions)
                        }
                    } else if (back != null && !largeTitle) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(back, Modifier.size(48.dp).semantics { contentDescription = "Back" }) {
                                Icon(painterResource(R.drawable.ic_chevron_down), null, Modifier.size(22.dp).then(Modifier.rotate(90f)), tint = Tokens.accent)
                            }
                            Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                            Row(horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically, content = actions)
                            Spacer(Modifier.width(12.dp))
                        }
                    } else if (LocalDensity.current.fontScale > 1.3f) {
                        Text(title, style = MaterialTheme.typography.headlineLarge.copy(
                            hyphens=androidx.compose.ui.text.style.Hyphens.Auto,
                            lineBreak=androidx.compose.ui.text.style.LineBreak.Heading
                        ), modifier = Modifier.fillMaxWidth().semantics { heading() })
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, content = actions)
                    } else Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
                        actions()
                    }
                    if (subtitle.isNotBlank()) {
                        if (onSubtitle != null) TextButton(onSubtitle, contentPadding = PaddingValues(0.dp)) { Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Tokens.muted) }
                        else Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Tokens.muted)
                    }
                }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(),
                contentPadding=PaddingValues(horizontal=Tokens.pageInset,vertical=12.dp),
                verticalArrangement=Arrangement.spacedBy(gap)) {
                content()
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }
}


@Composable fun Panel(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit) {
    Surface(modifier.fillMaxWidth(),shape=Tokens.card,color=Tokens.paper,border=BorderStroke(.7.dp,Tokens.border.copy(alpha=.65f))) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content)
    }
}
@Composable fun Field(label:String,value:String,onChange:(String)->Unit,numeric:Boolean=false) {
    val focus=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    OutlinedTextField(
        value,onChange,label={Text(label,style=MaterialTheme.typography.bodySmall)},
        modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(10.dp),textStyle=MaterialTheme.typography.bodyLarge,
        colors=OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor=Tokens.paper, focusedContainerColor=Tokens.paper,
            unfocusedBorderColor=Tokens.border, focusedBorderColor=Tokens.accent
        ),
        keyboardOptions=KeyboardOptions(keyboardType=if(numeric) KeyboardType.Decimal else KeyboardType.Text,imeAction=ImeAction.Done),
        keyboardActions=KeyboardActions(onDone={focus.clearFocus();keyboard?.hide()}),singleLine=true
    )
}

@Composable fun Choice(label:String,selected:String?,options:List<Pair<String,String>>,onSelect:(String)->Unit) =
    Choice(label,selected,options,icon=R.drawable.ic_basket,onSelect=onSelect)

@Composable fun Choice(label:String,selected:String?,options:List<Pair<String,String>>,icon:Int,onSelect:(String)->Unit) {
    var open by androidx.compose.runtime.saveable.rememberSaveable {mutableStateOf(false)}
    var search by androidx.compose.runtime.saveable.rememberSaveable {mutableStateOf("")}
    Column {
        Surface(onClick={open=true},modifier=Modifier.fillMaxWidth().heightIn(min=56.dp),shape=Tokens.field,color=Tokens.inputSurface) {
            Row(Modifier.padding(horizontal=16.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                Icon(painterResource(if(label.contains("account",true)) R.drawable.ic_account else icon),null,Modifier.size(22.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(label,style=MaterialTheme.typography.labelMedium,color=Tokens.muted)
                    Text(options.find {it.first==selected}?.second?:"Choose",style=MaterialTheme.typography.bodyLarge)
                }
                Icon(painterResource(R.drawable.ic_chevron_down),contentDescription=null,modifier=Modifier.size(18.dp),tint=Tokens.muted)
            }
        }
    }
    if(open) EditorialBottomSheet("Choose ${label.lowercase()}",{open=false;search=""}) {
        Field("Search options",search,{search=it})
        val matches=options.filter {it.second.contains(search,true)}
        if(matches.isEmpty()) Text(if(options.isEmpty()) "No options yet" else "No matching options",color=Tokens.muted)
        LazyColumn(Modifier.fillMaxWidth().heightIn(max=300.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            items(matches,key={it.first}) {(id,name)->Surface(onClick={onSelect(id);open=false;search=""},modifier=Modifier.fillMaxWidth().heightIn(min=56.dp).semantics {this.selected=selected==id},shape=Tokens.field,color=if(selected==id) Tokens.secondarySurface else Tokens.surface) {
                Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically) {Text(name,Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge);if(selected==id) Text("✓",Modifier.padding(start=12.dp))}
            }}
        }
        OutlinedButton({open=false;search=""},Modifier.fillMaxWidth()) {Text("Close")}
    }
}
@Composable fun EntryMode(selected:String,onSelect:(String)->Unit) {
    EditorialSegmentedControl(selected,listOf("Describe" to "Describe","Manual" to "Manual"),onSelect)
}
@Composable fun EditorialSegmentedControl(selected:String,options:List<Pair<String,String>>,onSelect:(String)->Unit) {
    if(options.isEmpty()) return
    val density=LocalDensity.current
    val measurer=rememberTextMeasurer()
    val style=MaterialTheme.typography.labelMedium
    val labelWidth=with(density) {options.maxOf {measurer.measure(it.second,style).size.width}.toDp()}
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stacked=density.fontScale>1.3f || (maxWidth-8.dp)/options.size<labelWidth+24.dp
        if(stacked) {
            Column(Modifier.fillMaxWidth().selectableGroup(),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                options.forEach {(id,label)->Surface(onClick={onSelect(id)},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).semantics {this.selected=id==selected},shape=Tokens.field,color=if(id==selected) Tokens.accent else Tokens.secondarySurface,contentColor=if(id==selected) Color.White else Tokens.text) {
                    Box(Modifier.padding(12.dp),contentAlignment=Alignment.CenterStart) {Text(label,style=style)}
                }}
            }
        } else Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).selectableGroup().background(Tokens.secondarySurface,CircleShape).padding(4.dp)) {
            options.forEach {(id,label)->
                val fill by androidx.compose.animation.animateColorAsState(if(id==selected) Tokens.accent else Tokens.secondarySurface, label="segment fill")
                Surface(onClick={onSelect(id)},modifier=Modifier.weight(1f).fillMaxHeight().heightIn(min=48.dp).semantics {this.selected=id==selected},shape=CircleShape,color=fill,contentColor=if(id==selected) Color.White else Tokens.text) {
                    Box(Modifier.padding(horizontal=12.dp,vertical=8.dp),contentAlignment=Alignment.Center) {
                        Text(label,style=style,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
        }
    }
}
@Composable fun CircleQuickAction(label:String,icon:Int,selected:Boolean=false,onClick:()->Unit) {
    Column(Modifier.clickable(role=androidx.compose.ui.semantics.Role.Button,onClick=onClick),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Surface(shape=CircleShape,color=if(selected) Tokens.accent else Tokens.secondarySurface,modifier=Modifier.size(56.dp)) {
            Box(contentAlignment=Alignment.Center) {Icon(painterResource(icon),null,Modifier.size(25.dp),tint=if(selected) MaterialTheme.colorScheme.onPrimary else Tokens.accent)}
        }
        Text(label,style=MaterialTheme.typography.labelMedium,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
    }
}
@Composable fun TransactionTypes(kind:com.smartexpense.domain.Kind,shared:Boolean,onSelect:(com.smartexpense.domain.Kind,Boolean)->Unit) {
    val choices=listOf(Triple("Expense",com.smartexpense.domain.Kind.EXPENSE,R.drawable.ic_arrow_out),Triple("Income",com.smartexpense.domain.Kind.INCOME,R.drawable.ic_arrow_in),Triple("Transfer",com.smartexpense.domain.Kind.TRANSFER,R.drawable.ic_transfer),Triple("Split bill",com.smartexpense.domain.Kind.EXPENSE,R.drawable.ic_people))
    val large=androidx.compose.ui.platform.LocalDensity.current.fontScale>1.3f
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        choices.chunked(if(large) 2 else 4).forEach {row->Row(Modifier.fillMaxWidth()) {
            row.forEach {(label,value,icon)->Box(Modifier.weight(1f),contentAlignment=Alignment.Center) {
                CircleQuickAction(label,icon,if(label=="Split bill") shared else value==kind && !shared) {onSelect(value,label=="Split bill")}
            }}
        }}
    }
}
@Composable fun EditorialPrimaryButton(label:String,enabled:Boolean=true,onClick:()->Unit) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Button(lightHapticAction(onClick),Modifier.fillMaxWidth().heightIn(min=48.dp).softPress(interaction),interactionSource=interaction,enabled=enabled,shape=Tokens.field,
        colors=ButtonDefaults.buttonColors(containerColor=Tokens.accent,contentColor=MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor=MaterialTheme.colorScheme.surfaceContainerHighest,disabledContentColor=Tokens.muted),
        contentPadding=PaddingValues(horizontal=20.dp,vertical=12.dp)) {
        Text(label,textAlign=androidx.compose.ui.text.style.TextAlign.Center,style=MaterialTheme.typography.labelLarge)
    }
}
@Composable fun AmountRow(label:String,amount:Long,color:Color=Tokens.text) {
    if(androidx.compose.ui.platform.LocalDensity.current.fontScale>1.3f || editorialMoney(amount).length>16) {
        Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(label,color=Tokens.muted,style=MaterialTheme.typography.bodyMedium)
            Text(editorialMoney(amount),color=color,style=MaterialTheme.typography.titleSmall)
        }
        return
    }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.Top) {
        Text(label,Modifier.weight(1f),color=Tokens.muted,style=MaterialTheme.typography.bodyMedium)
        Text(editorialMoney(amount),color=color,style=MaterialTheme.typography.titleSmall,textAlign=androidx.compose.ui.text.style.TextAlign.End)
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable fun ChipChoices(selected:String?,options:List<Pair<String,String>>,onSelect:(String)->Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        options.forEach {(id,label)->FilterChip(selected==id,{onSelect(id)},label={Text(label,style=MaterialTheme.typography.labelMedium,maxLines=1)},shape=CircleShape,border=null,colors=FilterChipDefaults.filterChipColors(containerColor=Tokens.surface,selectedContainerColor=Tokens.accent,selectedLabelColor=MaterialTheme.colorScheme.onPrimary))}
    }
}
fun personLabel(person:com.smartexpense.domain.Person,people:List<com.smartexpense.domain.Person>):String {
    val matching=people.filter {it.name.equals(person.name,true)}
    val alias=person.aliases.firstOrNull()?.takeIf {candidate->candidate.isNotBlank() && matching.count {other->other.aliases.firstOrNull()?.equals(candidate,true)==true}==1}
    return if(matching.size<=1) person.name else "${person.name} · ${alias?:"Contact ${matching.indexOfFirst {it.id==person.id}+1}"}"
}
@Composable fun DateTimeField(value:String,onChange:(String)->Unit) {
    val current=runCatching {java.time.LocalDateTime.parse(value)}.getOrElse {java.time.LocalDateTime.now()}
    var dateOpen by androidx.compose.runtime.saveable.rememberSaveable {mutableStateOf(false)}
    var timeOpen by androidx.compose.runtime.saveable.rememberSaveable {mutableStateOf(false)}
    Text("Date & time",color=Tokens.muted,style=MaterialTheme.typography.labelMedium)
    Column {
        OutlinedButton({dateOpen=true},Modifier.fillMaxWidth()) {Text(current.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy")))}
        OutlinedButton({timeOpen=true},Modifier.fillMaxWidth()) {Text(current.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")))}
    }
    if(dateOpen) EditorialDatePicker("Choose date",current.toLocalDate(),{dateOpen=false}) {onChange(java.time.LocalDateTime.of(it,current.toLocalTime()).toString());dateOpen=false}
    if(timeOpen) EditorialTimePicker(current.toLocalTime(),{timeOpen=false}) {onChange(java.time.LocalDateTime.of(current.toLocalDate(),it).toString());timeOpen=false}
}
@Composable fun SectionHeading(title:String,action:String?=null,onAction:()->Unit={}) {
    if(action!=null && androidx.compose.ui.platform.LocalDensity.current.fontScale>1.3f) {
        Column(Modifier.fillMaxWidth()) {
            Text(title,style=MaterialTheme.typography.titleLarge,modifier=Modifier.semantics {heading()})
            TextButton(onAction,contentPadding=PaddingValues(0.dp)) {Text(action,style=MaterialTheme.typography.labelMedium)}
        }
        return
    }
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text(title,Modifier.weight(1f).semantics {heading()},style=MaterialTheme.typography.titleLarge)
        if(action!=null) TextButton(onAction) {Text(action,style=MaterialTheme.typography.labelMedium)}
    }
}
@Composable fun EmptyLedger(title:String,description:String,action:String?=null,onAction:()->Unit={}) {
    Column(Modifier.fillMaxWidth().padding(vertical=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Icon(painterResource(R.drawable.ic_activity),contentDescription=null,tint=Tokens.muted,modifier=Modifier.size(28.dp))
        Text(title,style=MaterialTheme.typography.titleMedium)
        Text(description,color=Tokens.muted,style=MaterialTheme.typography.bodyMedium)
        if(action!=null) FilledTonalButton(onAction) {Text(action)}
    }
}
@Composable fun InitialAvatar(name:String,modifier:Modifier=Modifier) {
    val tone=when(name.trim().firstOrNull()?.uppercaseChar()) {'Y'->0;'R','F'->1;'A','W'->2;else->Math.floorMod(name.hashCode(),3)+1}
    val background=when(tone) {0->Color(0xFFDDEBFF);1->Color(0xFFFFE3D3);2->Color(0xFFE8DDFF);else->Color(0xFFFFE4EB)}
    val foreground=when(tone) {0->Tokens.accent;1->Color(0xFFAD4D19);2->Color(0xFF7952CB);else->Color(0xFFB53061)}
    Box(modifier.size(40.dp).background(background,CircleShape),contentAlignment=Alignment.Center) {
        Text(name.trim().take(1).uppercase(),style=MaterialTheme.typography.titleMedium,color=foreground)
    }
}








@OptIn(ExperimentalMaterial3Api::class)
@Composable fun EditorialBottomSheet(title:String,onDismiss:()->Unit,content:@Composable ColumnScope.()->Unit) {
    EditorialSheet(title,onDismiss,{Spacer(Modifier.width(48.dp))},content)
}
@Composable internal fun EditorialSaveSheet(title:String,onDismiss:()->Unit,enabled:Boolean,onSave:()->Unit,content:@Composable ColumnScope.()->Unit) {
    EditorialSheet(title,onDismiss,{TextButton(onSave,enabled=enabled,contentPadding=PaddingValues(horizontal=0.dp,vertical=8.dp)) {Text("Save",style=MaterialTheme.typography.bodyMedium)}},content)
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun EditorialSheet(title:String,onDismiss:()->Unit,action:@Composable ()->Unit,content:@Composable ColumnScope.()->Unit) {
    val density=androidx.compose.ui.platform.LocalDensity.current
    val height=with(density) {androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.height.toDp()}*0.88f
    val state=rememberModalBottomSheetState(skipPartiallyExpanded=true)
    ModalBottomSheet(onDismissRequest=onDismiss,sheetState=state,containerColor=Tokens.paper,contentColor=Tokens.text,shape=Tokens.card) {
        CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides density) {
        Column(Modifier.fillMaxWidth().heightIn(max=height).imePadding().navigationBarsPadding()) {
            Column(Modifier.fillMaxWidth().padding(horizontal=24.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                if(density.fontScale>1.3f) {
                    Text(title,Modifier.fillMaxWidth().semantics {heading()},style=MaterialTheme.typography.titleMedium)
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        TextButton(onDismiss,contentPadding=PaddingValues(horizontal=0.dp,vertical=8.dp)) {Text("Cancel",style=MaterialTheme.typography.bodyMedium)}
                        Spacer(Modifier.weight(1f));action()
                    }
                } else Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    TextButton(onDismiss,contentPadding=PaddingValues(horizontal=0.dp,vertical=8.dp)) {Text("Cancel",style=MaterialTheme.typography.bodyMedium)}
                    Text(title,Modifier.weight(1f).semantics {heading()},style=MaterialTheme.typography.titleMedium,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                    action()
                }
            }
            HorizontalDivider(color=Tokens.border)
            Column(Modifier.weight(1f,fill=false).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal=24.dp,vertical=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                content()
            }
        }
        }
    }
}


/** Presentation formatting only; ledger values remain exact integer paise. */
fun editorialMoney(paise:Long):String {
    val amount=java.math.BigDecimal.valueOf(paise,2)
    val formatter=java.text.NumberFormat.getNumberInstance(java.util.Locale.US).apply {minimumFractionDigits=2;maximumFractionDigits=2}
    return (if(paise<0) "−₹" else "₹")+formatter.format(amount.abs())
}
