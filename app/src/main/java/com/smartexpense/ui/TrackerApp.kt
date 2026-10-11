package com.smartexpense.ui
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.smartexpense.domain.*

@Composable fun TrackerApp(model:AppViewModel) {
    val ui by model.state.collectAsStateWithLifecycle();val form by model.entry.collectAsStateWithLifecycle()
    val repaymentDraft by model.repaymentDraft.collectAsStateWithLifecycle()
    val reportMonth by model.reportMonth.collectAsStateWithLifecycle()
    val reviewMonth by model.reviewMonth.collectAsStateWithLifecycle()
    val review by model.monthlyReview.collectAsStateWithLifecycle()
    val pendingReview by model.pendingReview.collectAsStateWithLifecycle()
    val nav=rememberNavController();val current by nav.currentBackStackEntryAsState();val route=current?.destination?.route
    val paymentAccounts=walletPaymentAccounts(ui.snapshot)
    val snackbar=remember {SnackbarHostState()}
    LaunchedEffect(ui.message) {ui.message?.let {snackbar.showSnackbar(it);model.dismissMessage()}}
    LaunchedEffect(repaymentDraft) {if(repaymentDraft!=null) nav.navigate("repayment/all")}
    LaunchedEffect(route) {model.observeRoute(route?:"home")}
    LaunchedEffect(ui.loading,ui.snapshot.localSettings.welcomeDone) {
        if(!ui.loading && !ui.snapshot.localSettings.welcomeDone && route!="welcome") nav.navigate("welcome") {launchSingleTop=true}
    }
    LaunchedEffect(ui.loading,pendingReview,ui.snapshot.localSettings.welcomeDone) {
        if(!ui.loading && ui.snapshot.localSettings.welcomeDone && pendingReview!=null) {model.selectReviewMonth(pendingReview!!);nav.navigate("monthly-review") {launchSingleTop=true}}
    }
    fun add(kind:Kind=Kind.EXPENSE,shared:Boolean=false) {model.newEntry(kind,shared);nav.navigate("add")}
    fun detail(id:String) {nav.navigate("detail/$id")}
    Scaffold(snackbarHost={SnackbarHost(snackbar)},bottomBar={
        if(route!="welcome" && WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current)==0) LedgerNavigation(route) {destination->nav.navigate(destination) {popUpTo("home") {saveState=true};launchSingleTop=true;restoreState=true}}
    }) {padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if(ui.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(ui.loading) Box(Modifier.fillMaxSize(),contentAlignment=androidx.compose.ui.Alignment.Center) {CircularProgressIndicator()} else CompositionLocalProvider(LocalEditorialBack provides if(route!=null && route !in listOf("home","activity","add","split","insights")) ({nav.popBackStack();Unit}) else null) {NavHost(nav,startDestination="home",modifier=Modifier.weight(1f)) {
                composable("home") {HomeScreen(ui.snapshot,::add,{nav.navigate("accounts")},{nav.navigate("repayment/all")},::detail,{nav.navigate(it)})}
                composable("activity") {ActivityScreen(ui.snapshot,::detail,onCalendar={nav.navigate("calendar")})}
                composable("calendar") {SpendingCalendarScreen(ui.snapshot,::detail,onDiary={nav.navigate("diary/$it")})}
                composable("gpay-settings") {GpaySettingsScreen(ui.snapshot,ui.busy,onBalance={amount,finished->model.action("GPay balance updated") {model.repository.reconcile(ui.snapshot.walletSources!!.gpayId,amount);finished()}},onSchedule={enabled,target,day,month,finished->model.action("GPay reset settings saved") {model.repository.saveGpaySchedule(enabled,target,day,month);finished()}},onSetup={nav.navigate("wallet")})}
                fun androidx.navigation.NavGraphBuilder.diaryRoute(path:String) {composable(path) {entry->DiaryScreen(ui.snapshot,ui.busy,
                    onSave={date,text,finished->model.action("Diary saved") {model.repository.saveDiary(date,text);finished()}},
                    onAdd={date,shared->model.newEntry(Kind.EXPENSE,shared);model.change(model.entry.value.copy(date=date.atTime(12,0).toString()));nav.navigate("add")},onDetails=::detail,initialDate=entry.arguments?.getString("date")?:java.time.LocalDate.now().toString())}}
                diaryRoute("diary");diaryRoute("diary/{date}")
                composable("monthly-review") {MonthlyReviewScreen(ui.snapshot,review,reviewMonth,model::selectReviewMonth) {model.acknowledgeReview {nav.popBackStack()}}}
                composable("profile") {ProfileScreen(ui.snapshot.localSettings,ui.busy,{name,avatar,finished->model.action("Profile saved") {model.repository.saveProfile(name,avatar);finished()}},{nav.navigate("welcome")})}
                composable("default-splits") {GroupDefaultsScreen(ui.snapshot,ui.busy,{value->model.action("Group default saved") {model.repository.saveGroupDefault(value)}},{nav.navigate("groups")})}
                composable("recurring") {RecurringScreen(ui.snapshot,ui.busy,{template,finished->model.action("Recurring template saved") {model.repository.saveTemplate(template);finished()}},{key->model.action("Template deleted") {model.repository.deletePlan(key)}},{template,date->model.recurringEntry(template,date);nav.navigate("add")})}
                composable("goals") {GoalsScreen(ui.snapshot,ui.busy,{name,target,id,finished->model.action("Goal saved") {model.repository.saveGoal(name,target,id);finished()}},{id,amount,finished->model.action("Planned allocation saved") {model.repository.allocateGoal(id,amount);finished()}},{key->model.action("Goal deleted") {model.repository.deletePlan(key)}})}
                composable("welcome") {WelcomeScreen(ui.busy) {createProfile->model.action("Welcome") {model.repository.finishWelcome();nav.navigate(if(createProfile) "profile" else "home") {popUpTo("home")}}}}
                composable("wallet") {WalletScreen(ui.snapshot,ui.busy,
                    onSetup={opening,wallet,gpay,finished->model.action("Wallet & GPay ready") {model.repository.configureWallet(opening,wallet,gpay);finished()}},
                    onBalance={account,amount,topup,finished->model.action("Wallet balance updated") {if(topup) model.repository.addWalletFunds(account,amount) else model.repository.reconcile(account,amount);finished()}},
                    onEntry={kind,account,destination->model.newEntry(kind);model.change(model.entry.value.copy(accountId=account,destinationId=destination.orEmpty()));nav.navigate("add")},
                    onRepay={nav.navigate("repayment/all")},onAccounts={nav.navigate("accounts")},
                    onSetupManual={walletAmount,gpayAmount,wallet,gpay,finished->model.action("Wallet & GPay ready") {model.repository.configureWallet(walletAmount,wallet,gpay,gpayOpening=gpayAmount);finished()}},onGpay={nav.navigate("gpay-settings")} )}
                composable("add") {EntryScreen(form,paymentAccounts,ui.snapshot.people,ui.snapshot.categories,ui.snapshot.groups,{value->val group=ui.snapshot.groups.find {it.id==value.groupId && value.groupId!=form.groupId};model.change(if(group==null) value else model.withGroupDefault(value,group))},model::parseText,onSave={model.saveEntry {nav.navigate("home") {popUpTo("home") {inclusive=true}}}},records=ui.snapshot.records,onBack={nav.popBackStack()},onFriends={nav.navigate("people")},walletSources=ui.snapshot.walletSources)}
                composable("split") {SplitScreen(ui.snapshot,{add(Kind.EXPENSE,true)},{nav.navigate("people")},{nav.navigate("groups")},{debt,group->nav.navigate("repayment/${debt?.id?:"all"}?group=${group.orEmpty()}")},::detail,onOffset={a,b,amount,group->model.action("Mutual offset recorded") {model.repository.recordOffset(a,b,amount,group)}},onRepeatGroup={group->model.repeatGroup(group);nav.navigate("add")})}
                composable("insights") {InsightsScreen(ui.snapshot,reportMonth,model::selectMonth,onBudget={_,_,_->},onPlanner={nav.navigate("planner")},onBudgetSave={month,category,amount,finished->model.action("Budget saved") {model.repository.saveBudget(month,category,amount);finished()}},busy=ui.busy)}
                composable("planner") {MoneyPlannerScreen(ui.snapshot,onBack={nav.popBackStack()})}
                composable("accounts") {AccountsScreen(ui.snapshot,model,::detail)}
                composable("categories") {CategoriesScreen(ui.snapshot,model)}
                composable("people") {PeopleScreen(ui.snapshot,model,::detail)}
                composable("groups") {GroupsScreen(ui.snapshot,model)}
                composable("backup") {BackupScreen(model)}
                composable("repayment/{id}?group={group}",arguments=listOf(navArgument("group") {defaultValue=""})) {entry->DisposableEffect(entry) {onDispose {model.clearRepaymentDraft()}};RepaymentScreen(ui.snapshot,ui.snapshot.debts.find {it.id==entry.arguments?.getString("id")},model,repaymentDraft,entry.arguments?.getString("group")?.ifBlank {null}) {model.clearRepaymentDraft();nav.popBackStack()}}
                composable("detail/{id}") {entry->val id=entry.arguments?.getString("id").orEmpty();DetailScreen(id,ui.snapshot,model,{nav.navigate("add")}) {record ->
                    model.newEntry(Kind.REFUND);model.change(model.entry.value.copy(description="Refund: ${record.description}",categoryId=record.categoryId,refundOf=id));nav.navigate("add")
                }}
            }}
        }
    }
}

@Composable fun LedgerNavigation(route:String?,onNavigate:(String)->Unit) = EditorialBottomNavigation(route,onNavigate)
