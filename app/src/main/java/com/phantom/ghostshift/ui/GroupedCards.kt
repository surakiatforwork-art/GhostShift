package com.phantom.ghostshift.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phantom.ghostshift.ui.theme.*

@Composable
internal fun GroupHeader(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart=18.dp,topEnd=18.dp))
        .background(MintCardBg).padding(horizontal=12.dp,vertical=10.dp),content=content)
}

@Composable
internal fun GroupBody(modifier: Modifier = Modifier, lifted: Boolean = false, content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth().then(if (lifted) Modifier else Modifier.background(MintCardBg)).padding(horizontal=8.dp).padding(bottom=6.dp)) {
        Box(Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
internal fun GroupEnd() {
    Spacer(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(bottomStart=18.dp,bottomEnd=18.dp)).background(MintCardBg))
}

@Composable
internal fun CompactOverview(state: MainUiState, modifier: Modifier = Modifier, onClick: () -> Unit, horizontal: Boolean = false) {
    val done = state.downloadedPhotos.size
    val total = done + state.pendingPhotos.size
    val ratio = if (total == 0) 0f else done.toFloat()/total
    MintCard(modifier=modifier.clickable(onClick=onClick),containerColor=headerPastel(UtilityTone.Lavender),contentPadding=2.dp) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
        val showTitle = maxHeight >= 36.dp
        val badgeSize = (maxHeight * .85f).coerceIn(18.dp,44.dp)
        Row(Modifier.fillMaxSize(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp,Alignment.CenterHorizontally)) {
            val track = MintLine
            Box(Modifier.size(badgeSize),contentAlignment=Alignment.Center) {
                Canvas(Modifier.size(badgeSize - 3.dp)) {
                    val stroke=Stroke(4.dp.toPx(),cap=StrokeCap.Round)
                    drawArc(track,-90f,360f,false,style=stroke)
                    if(ratio>0f) drawArc(Color(0xFF8D6EE8),-90f,ratio*360f,false,style=stroke)
                }
                Text("${(ratio*100).toInt()}%",fontWeight=FontWeight.Bold,color=MintText,style=MaterialTheme.typography.labelMedium)
            }
            Column(horizontalAlignment=Alignment.Start) {
                if (showTitle) Text("ภาพรวม",style=MaterialTheme.typography.labelSmall,color=MintMuted)
                Text("$done/$total ภาพ",color=MintText,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.labelSmall,maxLines=1)
            }
        }
    }
}
}
