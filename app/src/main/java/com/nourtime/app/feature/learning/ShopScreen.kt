package com.nourtime.app.feature.learning

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nourtime.app.R
import com.nourtime.app.core.designsystem.theme.NourPalette
import com.nourtime.app.core.learning.NumeralStyle
import com.nourtime.app.data.learning.LearningState
import com.nourtime.app.data.settings.ChildGender
import com.nourtime.app.feature.lock.Gendered

/** A coin: bigger values are bigger coins, silver below 10, gold from 10. */
@Composable
private fun Coin(value: Int, numerals: NumeralStyle, size: Dp, onClick: () -> Unit) {
    val gold = value >= 10
    val description = stringResource(R.string.learn_shop_coin, numerals.format(value))
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (gold) Color(0xFFFFD54F) else Color(0xFFE0E0E0),
        shadowElevation = 3.dp,
        modifier = Modifier
            .size(size)
            .border(3.dp, if (gold) Color(0xFFF9A825) else Color(0xFF9E9E9E), CircleShape)
            .semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(numerals.format(value), fontSize = (size.value * 0.36f).sp, fontWeight = FontWeight.Bold, color = NourPalette.Navy)
        }
    }
}

/** Little Shop: a thing and its price; tap coins onto the counter to pay exactly (or give change). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColumnScope.ShopScreen(controller: LearningHubController, state: LearningState?, s: HubScreen.Shop, gender: ChildGender) {
    val numerals = controller.numerals(state)
    val look = lookOf(s.game)
    val round = s.round
    val speaker = LocalSpeaker.current
    val task = s.paidFor?.let { round.tasks[it] } ?: round.current ?: return
    // Amounts are in Kuwaiti dinars: "٤ د.ك" / "4 KD".
    @Composable
    fun money(v: Int) = stringResource(R.string.learn_shop_money, numerals.format(v))

    HubTopBar(stringResource(R.string.learn_level, numerals.format(s.level + 1)), { controller.back() })
    val message = when {
        s.paidFor != null -> stringResource(Gendered(R.string.learn_great_m, R.string.learn_great_f).pick(gender))
        s.lastOver -> stringResource(Gendered(R.string.learn_shop_over_m, R.string.learn_shop_over_f).pick(gender))
        task.change -> stringResource(Gendered(R.string.learn_shop_change_m, R.string.learn_shop_change_f).pick(gender), money(task.price), money(task.paid!!))
        else -> stringResource(Gendered(R.string.learn_shop_pay_m, R.string.learn_shop_pay_f).pick(gender), money(task.price))
    }
    Text(
        message,
        style = MaterialTheme.typography.titleLarge,
        color = if (s.paidFor != null) NourPalette.MintDeep else NourPalette.Navy,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    )
    Column(
        Modifier.weight(1f).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        // The thing with its price tag (and the note it was paid with).
        Box {
            Surface(shape = RoundedCornerShape(28.dp), color = NourPalette.White, shadowElevation = 3.dp) {
                Box(Modifier.padding(18.dp)) { LearningPicture(task.item.image, task.item.emoji, 104.dp, 72.sp, description = task.item.word) }
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = look.accent,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
            ) {
                Text(money(task.price), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = NourPalette.Navy, modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp))
            }
        }
        // The counter: coins put down so far (tap one to take it back) and their total.
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = NourPalette.White.copy(alpha = 0.7f),
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp).border(2.dp, look.accent, RoundedCornerShape(24.dp)),
        ) {
            Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val total = if (s.paidFor != null) task.target else round.total
                Text(stringResource(R.string.learn_shop_total, money(total)), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = NourPalette.Navy)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    if (s.paidFor == null) {
                        round.counter.forEachIndexed { i, c -> Coin(c, numerals, 46.dp) { controller.shopRemove(i) } }
                    }
                }
            }
        }
    }
    // The purse: one of each coin, as many times as needed.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).background(Color.Transparent),
    ) {
        round.coins.forEach { value ->
            Coin(value, numerals, (56 + minOf(value, 50) / 5).dp) {
                controller.shopAdd(value)
                speaker.say(value.toString(), controller.appLanguage)
            }
        }
    }
}
