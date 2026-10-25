package com.smartexpense.data

import android.content.Context
import android.util.Log
import com.smartexpense.BuildConfig
import com.smartexpense.domain.CommandExpenseTextParser
import com.smartexpense.domain.ParsedDraft
import com.smartexpense.domain.PersonRef
import com.smartexpense.domain.ScratchCommandModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Bundled model loaded once, with parsing and file IO kept off the UI thread. */
@Singleton
class LocalCommandParser @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val parser by lazy {
        val model = if (!BuildConfig.LOCAL_AI_ENABLED) null else try {
            context.assets.open("tally-command.bin").use(ScratchCommandModel::load)
        } catch (error: java.io.IOException) {
            Log.w("LocalCommandParser", "Command model unavailable; using rules", error)
            null
        } catch (error: IllegalArgumentException) {
            Log.w("LocalCommandParser", "Command model invalid; using rules", error)
            null
        }
        val extension = if (!BuildConfig.LOCAL_AI_ENABLED) null else try {
            context.assets.open("tally-command-extended.bin").use { ScratchCommandModel.load(it, true) }
        } catch (error: java.io.IOException) {
            Log.w("LocalCommandParser", "Extended command model unavailable; using compatibility model", error)
            null
        } catch (error: IllegalArgumentException) {
            Log.w("LocalCommandParser", "Extended command model invalid; using compatibility model", error)
            null
        }
        CommandExpenseTextParser(model, extension = extension)
    }

    suspend fun parse(text: String, people: List<PersonRef>): ParsedDraft =
        withContext(Dispatchers.Default) { parser.parse(text, people) }
}
