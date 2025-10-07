package org.hyperledger.ariesframework.credentials.formats.anoncreds

object MessageValidator {

    /**
     * @throws ClassValidationError se houver erros de validação
     */
    fun validateSync(classInstance: Any) {
        val errors = validate(classInstance)

        if (errors.isNotEmpty()) {
            throw ClassValidationError(
                message = "Failed to validate class.",
                classType = classInstance::class.simpleName ?: "UnknownClass",
                validationErrors = errors,
            )
        }
    }

    // Simula uma função de validação (você pode integrar com Bean Validation, etc.)
    private fun validate(obj: Any): List<String> {
        val violations = mutableListOf<String>()

        // Exemplo simples: reflexão ou validação manual
        if (obj is AnonCredsCredentialProposal && obj.schemaName.isNullOrBlank()) {
            violations.add("schemaName must not be blank")
        }

        return violations
    }
}

class ClassValidationError(
    override val message: String,
    val classType: String,
    val validationErrors: List<String> = emptyList(),
) : Exception(message)
