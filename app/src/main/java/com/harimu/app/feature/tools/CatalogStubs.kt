package com.harimu.app.feature.tools

import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.vector.ImageVector
import com.harimu.app.core.toolapi.InputSpec
import com.harimu.app.core.toolapi.ModelSpec
import com.harimu.app.core.toolapi.Tool
import com.harimu.app.core.toolapi.ToolContext
import com.harimu.app.core.toolapi.ToolGroup
import com.harimu.app.core.toolapi.ToolInput
import com.harimu.app.core.toolapi.ToolParam
import com.harimu.app.core.toolapi.ToolProgress
import com.harimu.app.core.toolapi.ToolResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class StubTool(
    override val id: String,
    override val group: ToolGroup,
    override val engineLabel: String,
    override val plannedPhase: String,
    override val icon: ImageVector,
    override val inputSpec: InputSpec,
    override val params: List<ToolParam> = emptyList(),
    override val requiresNetwork: Boolean = false,
    override val requiresModel: ModelSpec? = null,
    override val aliasOfId: String? = null,
    private val titleId: String,
    private val titleEn: String,
    private val descId: String,
    private val descEn: String
) : Tool {
    override val isStub: Boolean = true

    override fun title(isEnglish: Boolean): String = if (isEnglish) titleEn else titleId

    override fun description(isEnglish: Boolean): String =
        if (isEnglish) "Coming soon ($plannedPhase) — $descEn"
        else "Segera hadir ($plannedPhase) — $descId"

    override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
        emit(ToolProgress.Running(50, if (ctx.isEnglish) "Preparing stub ($plannedPhase)..." else "Menyiapkan stub ($plannedPhase)…"))
        delay(250L)
        val msg = if (ctx.isEnglish) "Coming soon in $plannedPhase ($id)" else "Segera hadir di $plannedPhase ($id)"
        emit(ToolProgress.Done(ToolResult(summary = msg)))
    }
}
