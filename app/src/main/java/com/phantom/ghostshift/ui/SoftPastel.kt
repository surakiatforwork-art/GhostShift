package com.phantom.ghostshift.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phantom.ghostshift.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun pairPastel(index: Int, active: Boolean, completed: Boolean, neutral: Boolean = false): Color {
    val dark = LocalGhostDark.current
    return when {
        completed -> if (dark) Color(0xFF233C37) else Color(0xFFE6F8EF)
        neutral -> if (dark) Color(0xFF292D38) else Color(0xFFF7F7FA)
        active -> if (dark) Color(0xFF403453) else Color(0xFFEDE1FF)
        index % 3 == 0 -> if (dark) Color(0xFF303D50) else Color(0xFFE3F0FF)
        index % 3 == 1 -> if (dark) Color(0xFF263F40) else Color(0xFFE1F5F2)
        else -> if (dark) Color(0xFF3D354D) else Color(0xFFF1E9FC)
    }
}

@Composable
internal fun headerPastel(tone: UtilityTone): Color {
    val dark = LocalGhostDark.current
    return when (tone) {
        UtilityTone.Mint -> if (dark) Color(0xFF263F40) else Color(0xFFE1F5F2)
        UtilityTone.Sky -> if (dark) Color(0xFF303D50) else Color(0xFFE3F0FF)
        UtilityTone.Lavender -> if (dark) Color(0xFF3D354D) else Color(0xFFF1E9FC)
        UtilityTone.Peach -> if (dark) Color(0xFF49382E) else Color(0xFFFFEBDD)
        else -> MintCardBg
    }
}

/** Shared mascot derived from the original launcher artwork. */
@Composable
internal fun GhostMark(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(com.phantom.ghostshift.R.drawable.phantom_mascot),
        contentDescription = null,
        modifier = modifier,
        contentScale = androidx.compose.ui.layout.ContentScale.Fit
    )
}

@Composable
internal fun GhostProgressSummary(state: MainUiState) {
    val completed = state.downloadedPhotos.size
    val pending = state.pendingPhotos.size
    val total = completed + pending
    val ready = state.pendingPhotos.count { it.filePath.isNotBlank() }
    val fraction = if (total == 0) 0f else completed.toFloat()/total
    MintCard(contentPadding=14.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(78.dp),contentAlignment=Alignment.Center) {
                val track=MintLine
                Canvas(Modifier.size(72.dp)) {
                    val stroke=Stroke(8.dp.toPx(),cap=StrokeCap.Round)
                    drawArc(track,-90f,360f,false,style=stroke)
                    if(fraction>0f) drawArc(Brush.sweepGradient(listOf(Color(0xFF9170EB),Color(0xFF20C9A0),Color(0xFF9170EB))),-90f,360f*fraction,false,style=stroke)
                }
                Column(horizontalAlignment=Alignment.CenterHorizontally) {
                    Text("${(fraction*100).toInt()}%",fontWeight=FontWeight.Bold,color=MintText,style=MaterialTheme.typography.titleMedium)
                    Text("$completed / $total",color=MintMuted,style=MaterialTheme.typography.labelSmall)
                }
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text("ภาพรวม",fontWeight=FontWeight.Bold,color=MintText,style=MaterialTheme.typography.titleSmall)
                ProgressLegend("ดาวน์โหลดแล้ว",completed,Color(0xFF20C9A0))
                ProgressLegend("พร้อมดาวน์โหลด",ready,Color(0xFF669BED))
                ProgressLegend("รอเพิ่มภาพ",pending-ready,Color(0xFFAA82E8))
            }
            GhostMark(Modifier.size(54.dp))
        }
    }
}

@Composable
private fun ProgressLegend(label:String,count:Int,color:Color) {
    Row(verticalAlignment=Alignment.CenterVertically) {
        Canvas(Modifier.size(7.dp)){drawCircle(color)}
        Spacer(Modifier.width(6.dp))
        Text(label,Modifier.weight(1f),color=MintMuted,style=MaterialTheme.typography.labelSmall)
        Text(count.toString(),color=MintText,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.labelSmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GhostActivityScreen(state:MainUiState,onBack:()->Unit) {
    val history=remember(state.downloadedPhotos){state.downloadedPhotos.sortedByDescending{it.downloadedAt}}
    Scaffold(containerColor=MintBg,topBar={
        TopAppBar(title={Text("สรุปงาน",fontWeight=FontWeight.Bold)},navigationIcon={
            IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"กลับ")}
        },colors=TopAppBarDefaults.topAppBarColors(containerColor=MintBg))
    }){padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal=14.dp),verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=24.dp)){
            item{GhostProgressSummary(state)}
            item{Text("ประวัติดาวน์โหลด",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold,color=MintText,modifier=Modifier.padding(vertical=6.dp))}
            if(history.isEmpty()) item{MintCard{Text("ยังไม่มีประวัติ",color=MintMuted)}}
            items(history,key={it.id}){photo->
                MintCard(contentPadding=12.dp){
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                        Icon(Icons.Default.CheckCircle,null,tint=Color(0xFF20B891))
                        Column(Modifier.weight(1f)){
                            Text(photo.tag,fontWeight=FontWeight.Bold,color=MintText)
                            photo.remark?.takeIf{it.isNotBlank()}?.let{Text(it,color=MintMuted,style=MaterialTheme.typography.bodySmall)}
                        }
                        Text(photo.downloadedAt?.let{SimpleDateFormat("dd/MM HH:mm",Locale.getDefault()).format(Date(it))}?:"–",style=MaterialTheme.typography.labelSmall,color=MintMuted)
                    }
                }
            }
        }
    }
}
