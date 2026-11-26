package com.harimu.app

import com.harimu.app.core.design.ParamValidation
import com.harimu.app.core.design.ParamValidators
import com.harimu.app.core.design.ToolMimeTypes
import com.harimu.app.core.design.ToolPickContracts
import com.harimu.app.core.design.getPickerLabel
import com.harimu.app.core.design.getSecondaryLabel
import com.harimu.app.core.design.requiresSecondaryInput
import com.harimu.app.core.design.toProgressState
import com.harimu.app.core.toolapi.InputSpec
import com.harimu.app.core.toolapi.ToolParam
import com.harimu.app.core.toolapi.ToolProgress
import com.harimu.app.core.toolapi.ToolResult
import com.harimu.app.core.toolapi.ToolOutputFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ToolWorkspaceComponentsTest {

    @Test
    fun paramValidatorsRequired() {
        val validator = ParamValidators.required("Field is required")
        assertTrue(validator("").isValid == false)
        assertTrue(validator("value").isValid)
        assertTrue(validator(false).isValid == false)
        assertTrue(validator(true).isValid)
    }

    @Test
    fun paramValidatorsMinLength() {
        val validator = ParamValidators.minLength(4, "Too short")
        assertTrue(validator("abc").isValid == false)
        assertTrue(validator("abcd").isValid)
        assertTrue(validator("abcde").isValid)
    }

    @Test
    fun paramValidatorsMaxLength() {
        val validator = ParamValidators.maxLength(10, "Too long")
        assertTrue(validator("12345678901").isValid == false)
        assertTrue(validator("1234567890").isValid)
    }

    @Test
    fun paramValidatorsRange() {
        val validator = ParamValidators.range(0, 100, "Out of range")
        assertTrue(validator(-1).isValid == false)
        assertTrue(validator(101).isValid == false)
        assertTrue(validator(0).isValid)
        assertTrue(validator(50).isValid)
        assertTrue(validator(100).isValid)
    }

    @Test
    fun paramValidatorsRegex() {
        val validator = ParamValidators.regex("^#[0-9A-Fa-f]{6}$", "Not hex")
        assertTrue(validator("#FF6B2C").isValid)
        assertTrue(validator("#123abc").isValid)
        assertTrue(validator("FF6B2C").isValid == false)
        assertTrue(validator("#GGGGGG").isValid == false)
    }

    @Test
    fun paramValidatorsEmail() {
        val validator = ParamValidators.email()
        assertTrue(validator("test@example.com").isValid)
        assertTrue(validator("user.name@domain.org").isValid)
        assertTrue(validator("invalid").isValid == false)
        assertTrue(validator("@domain.com").isValid == false)
    }

    @Test
    fun paramValidatorsHexColor() {
        val validator = ParamValidators.hexColor()
        assertTrue(validator("#FF6B2C").isValid)
        assertTrue(validator("#000000").isValid)
        assertTrue(validator("#ffffff").isValid)
        assertTrue(validator("FF6B2C").isValid == false)
        assertTrue(validator("#GGGGGG").isValid == false)
    }

    @Test
    fun paramValidatorsCombine() {
        val validator = ParamValidators.combine(
            ParamValidators.required("Required"),
            ParamValidators.minLength(3, "Min 3"),
            ParamValidators.maxLength(10, "Max 10")
        )
        assertTrue(validator("").isValid == false)
        assertTrue(validator("ab").isValid == false)
        assertTrue(validator("abcdefghijk").isValid == false)
        assertTrue(validator("valid").isValid)
    }

    @Test
    fun paramValidatorsWarnIf() {
        val validator = ParamValidators.warnIf({ it == "deprecated" }, "This value is deprecated")
        assertTrue(validator("ok").isValid)
        assertTrue(validator("ok").warningMessage == null)
        assertTrue(validator("deprecated").isValid)
        assertTrue(validator("deprecated").warningMessage == "This value is deprecated")
    }

    @Test
    fun toolMimeTypesForInputSpec() {
        assertEquals(ToolMimeTypes.IMAGE_ALL.size, ToolMimeTypes.forInputSpec(InputSpec.SingleImage).size)
        assertEquals(ToolMimeTypes.VIDEO_ALL.size, ToolMimeTypes.forInputSpec(InputSpec.SingleVideo).size)
        assertEquals(ToolMimeTypes.AUDIO_ALL.size, ToolMimeTypes.forInputSpec(InputSpec.SingleAudio).size)
        assertEquals(1, ToolMimeTypes.forInputSpec(InputSpec.SinglePdf).size)
        assertEquals(ToolMimeTypes.ARCHIVE_ALL.size, ToolMimeTypes.forInputSpec(InputSpec.ZipFile).size)
        assertEquals(1, ToolMimeTypes.forInputSpec(InputSpec.AnyFile).size)
    }

    @Test
    fun toolMimeTypesForSecondaryInput() {
        assertEquals(ToolMimeTypes.AUDIO_ALL.size, ToolMimeTypes.forSecondaryInput(InputSpec.VideoAndAudio).size)
        assertEquals(ToolMimeTypes.IMAGE_ALL.size, ToolMimeTypes.forSecondaryInput(InputSpec.VideoAndImage).size)
        assertEquals(ToolMimeTypes.SUBTITLE_ALL.size, ToolMimeTypes.forSecondaryInput(InputSpec.VideoAndSubtitle).size)
        assertEquals(2, ToolMimeTypes.forSecondaryInput(InputSpec.VideoAndMedia).size)
        assertEquals(ToolMimeTypes.IMAGE_ALL.size, ToolMimeTypes.forSecondaryInput(InputSpec.AudioAndImage).size)
        assertEquals(ToolMimeTypes.AUDIO_ALL.size, ToolMimeTypes.forSecondaryInput(InputSpec.AudioAndWatermarkAudio).size)
        assertEquals(ToolMimeTypes.IMAGE_ALL.size, ToolMimeTypes.forSecondaryInput(InputSpec.PdfAndImage).size)
    }

    @Test
    fun toolMimeTypesDescribeAcceptedTypes() {
        assertTrue(ToolMimeTypes.describeAcceptedTypes(InputSpec.SingleImage).contains("Images"))
        assertTrue(ToolMimeTypes.describeAcceptedTypes(InputSpec.SingleVideo).contains("Videos"))
        assertTrue(ToolMimeTypes.describeAcceptedTypes(InputSpec.SingleAudio).contains("Audio"))
        assertTrue(ToolMimeTypes.describeAcceptedTypes(InputSpec.SinglePdf).contains("PDF"))
        assertTrue(ToolMimeTypes.describeAcceptedTypes(InputSpec.ZipFile).contains("ZIP"))
    }

    @Test
    fun inputSpecExtensions() {
        assertEquals("Select Image", InputSpec.SingleImage.getPickerLabel(false, true))
        assertEquals("Select Images", InputSpec.MultipleImages.getPickerLabel(true, true))
        assertEquals("Select Video", InputSpec.SingleVideo.getPickerLabel(false, true))
        assertEquals("Select Files", InputSpec.AnyFile.getPickerLabel(true, true))
        assertEquals("Enter Text", (InputSpec.TextInput("", "", "") as InputSpec).getPickerLabel(false, true))

        assertTrue(InputSpec.VideoAndAudio.requiresSecondaryInput())
        assertTrue(InputSpec.VideoAndImage.requiresSecondaryInput())
        assertTrue(InputSpec.VideoAndSubtitle.requiresSecondaryInput())
        assertTrue(InputSpec.VideoAndMedia.requiresSecondaryInput())
        assertTrue(InputSpec.AudioAndImage.requiresSecondaryInput())
        assertTrue(InputSpec.AudioAndWatermarkAudio.requiresSecondaryInput())
        assertTrue(InputSpec.PdfAndImage.requiresSecondaryInput())
        assertFalse(InputSpec.SingleImage.requiresSecondaryInput())
        assertFalse(InputSpec.SingleVideo.requiresSecondaryInput())

        assertEquals("Background Audio", InputSpec.VideoAndAudio.getSecondaryLabel(true))
        assertEquals("Overlay Image", InputSpec.VideoAndImage.getSecondaryLabel(true))
        assertEquals("Subtitle File", InputSpec.VideoAndSubtitle.getSecondaryLabel(true))
        assertEquals("Background Media", InputSpec.VideoAndMedia.getSecondaryLabel(true))
        assertEquals("Cover Image", InputSpec.AudioAndImage.getSecondaryLabel(true))
        assertEquals("Watermark Audio", InputSpec.AudioAndWatermarkAudio.getSecondaryLabel(true))
        assertEquals("Watermark Image", InputSpec.PdfAndImage.getSecondaryLabel(true))
        assertNull(InputSpec.SingleImage.getSecondaryLabel(true))
    }

    @Test
    fun toolProgressToProgressState() {
        val queued = ToolProgress.Queued
        val running = ToolProgress.Running(50, "Processing...")
        val done = ToolProgress.Done(ToolResult(
            outputs = listOf(
                ToolOutputFile(File("test.txt"), "text/plain", "test.txt"),
                ToolOutputFile(File("test2.txt"), "text/plain", "test2.txt")
            ),
            textOutput = "output text",
            summary = "Done"
        ))
        val failed = ToolProgress.Failed("Error occurred")

        val queuedState = queued.toProgressState()
        assertTrue(queuedState is com.harimu.app.core.design.ToolProgressState.Queued)

        val runningState = running.toProgressState()
        assertTrue(runningState is com.harimu.app.core.design.ToolProgressState.Running)
        assertEquals(50, (runningState as com.harimu.app.core.design.ToolProgressState.Running).percent)
        assertEquals("Processing...", (runningState as com.harimu.app.core.design.ToolProgressState.Running).message)

        val doneState = done.toProgressState()
        assertTrue(doneState is com.harimu.app.core.design.ToolProgressState.Done)
        assertEquals("Done", (doneState as com.harimu.app.core.design.ToolProgressState.Done).result.summary)
        assertEquals(2, (doneState as com.harimu.app.core.design.ToolProgressState.Done).result.outputCount)
        assertEquals("text/plain", (doneState as com.harimu.app.core.design.ToolProgressState.Done).result.primaryMimeType)
        assertTrue((doneState as com.harimu.app.core.design.ToolProgressState.Done).result.hasTextOutput)

        val failedState = failed.toProgressState()
        assertTrue(failedState is com.harimu.app.core.design.ToolProgressState.Failed)
        assertEquals("Error occurred", (failedState as com.harimu.app.core.design.ToolProgressState.Failed).error)
        assertTrue((failedState as com.harimu.app.core.design.ToolProgressState.Failed).isRetryable)
    }

    @Test
    fun toolPickContractsCreatePickRequest() {
        assertNotNull(ToolPickContracts.createPickRequest(InputSpec.SingleImage))
        assertNotNull(ToolPickContracts.createPickRequest(InputSpec.MultipleImages))
        assertNotNull(ToolPickContracts.createPickRequest(InputSpec.SingleVideo))
        assertNotNull(ToolPickContracts.createPickRequest(InputSpec.MultipleVideos))
        assertNull(ToolPickContracts.createPickRequest(InputSpec.SingleAudio))
        assertNull(ToolPickContracts.createPickRequest(InputSpec.SinglePdf))
    }

    @Test
    fun toolPickContractsCreateDocumentContract() {
        val audioContract = ToolPickContracts.createDocumentContract(InputSpec.SingleAudio)
        assertNotNull(audioContract)

        val pdfContract = ToolPickContracts.createDocumentContract(InputSpec.SinglePdf)
        assertNotNull(pdfContract)

        val multiPdfContract = ToolPickContracts.createDocumentContract(InputSpec.MultiplePdfs)
        assertNotNull(multiPdfContract)

        val zipContract = ToolPickContracts.createDocumentContract(InputSpec.ZipFile)
        assertNotNull(zipContract)
    }
}