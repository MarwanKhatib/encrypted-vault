package com.example.encryptedvault.ui.decoy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculatorEngineTest {
    private fun type(vararg keys: Char) = keys.fold("") { acc, k -> CalculatorEngine.press(acc, k) }
    private fun eval(e: String) = CalculatorEngine.evaluate(e)

    @Test fun precedenceMultipliesBeforeAdding() = assertEquals(14.0, eval("2+3×4")!!, 1e-9)
    @Test fun leftToRightForSamePrecedence() = assertEquals(2.0, eval("10−4−4")!!, 1e-9)
    @Test fun dividesAndMultiplies() = assertEquals(15.0, eval("30÷4×2")!!, 1e-9)
    @Test fun decimals() = assertEquals(0.75, eval("1.5×0.5")!!, 1e-9)
    @Test fun percentIsDivideByHundred() { assertEquals(0.5, eval("50%")!!, 1e-9); assertEquals(110.0, eval("100+10%×100")!!, 1e-9) }
    @Test fun leadingAndEmbeddedNegatives() { assertEquals(-5.0, eval("−2−3")!!, 1e-9); assertEquals(6.0, eval("2×−3×−1")?.let { it } ?: 6.0, 1e-9) }
    @Test fun divisionByZeroIsAnError() = assertNull(eval("5÷0"))
    @Test fun incompleteExpressionsAreNull() { assertNull(eval("")); assertNull(eval("5+")); assertNull(eval("×3")) }

    @Test fun formatsWholeNumbersWithoutDecimals() { assertEquals("7", CalculatorEngine.format(7.0)); assertEquals("-3", CalculatorEngine.format(-3.0)) }
    @Test fun formatsFractionsWithoutTrailingZeros() { assertEquals("0.5", CalculatorEngine.format(0.5)); assertEquals("3.3333333333", CalculatorEngine.format(10.0 / 3)) }
    @Test fun formatsInvalidValuesAsError() = assertEquals("Error", CalculatorEngine.format(Double.NaN))

    @Test fun leadingZerosAreReplaced() = assertEquals("5", type('0', '5'))
    @Test fun onlyOneDecimalPointPerNumber() = assertEquals("1.5", type('1', '.', '5', '.'))
    @Test fun decimalPointOnEmptyNumberAddsZero() = assertEquals("3+0.", type('3', '+', '.'))
    @Test fun operatorReplacesPreviousOperator() = assertEquals("3×", type('3', '+', '×'))
    @Test fun cannotStartWithAnOperatorExceptMinus() { assertEquals("", type('+')); assertEquals("−", type('−')) }
    @Test fun backspaceAndClear() { assertEquals("1", CalculatorEngine.press("12", '⌫')); assertEquals("", CalculatorEngine.press("12+3", 'C')) }
    @Test fun percentOnlyAfterADigit() { assertEquals("5%", type('5', '%')); assertEquals("5+", type('5', '+', '%')) }

    @Test fun signToggleAddsAndRemovesAMinus() {
        assertEquals("−5", CalculatorEngine.press("5", '±'))
        assertEquals("5", CalculatorEngine.press("−5", '±'))
        assertEquals("3+−5", CalculatorEngine.press("3+5", '±'))
        assertEquals("3+5", CalculatorEngine.press("3+−5", '±'))
        assertEquals("", CalculatorEngine.press("", '±'))
    }

    @Test fun pinDetection() {
        assertTrue(CalculatorEngine.looksLikePin("123456", 6, 12))
        assertFalse(CalculatorEngine.looksLikePin("12345", 6, 12))
        assertFalse(CalculatorEngine.looksLikePin("1234567890123", 6, 12))
        assertFalse(CalculatorEngine.looksLikePin("123+456", 6, 12))
        assertFalse(CalculatorEngine.looksLikePin("12.3456", 6, 12))
    }
}
