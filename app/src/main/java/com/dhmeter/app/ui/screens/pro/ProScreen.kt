package com.dropindh.app.ui.screens.pro

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dropindh.app.monetization.BillingSubscriptionProduct
import com.dropindh.app.monetization.MonetizationCatalog
import com.dropindh.app.ui.i18n.tr
import com.dropindh.app.ui.theme.dhGlassCardColors
import com.dropindh.app.ui.theme.dhTopBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProScreen(
    onBack: () -> Unit,
    viewModel: ProViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? Activity

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = dhTopBarColors(),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.WorkspacePremium, contentDescription = null)
                        Text(
                            text = tr("dropIn DH Pro", "dropIn DH Pro"),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = tr("Back", "Atras")
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = tr("Refresh", "Actualizar")
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ProValueCard(
                    isPro = uiState.isPro,
                    activeProductId = uiState.activeProductId
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        modifier = Modifier.weight(1f),
                        onClick = viewModel::restorePurchases
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null)
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(tr("Restore", "Restaurar"))
                    }
                    FilledTonalButton(
                        modifier = Modifier.weight(1f),
                        onClick = viewModel::refresh
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(tr("Sync", "Sincronizar"))
                    }
                }
            }

            if (uiState.isLoading && uiState.products.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = dhGlassCardColors()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = tr(
                                    "Loading subscription plans...",
                                    "Cargando planes de suscripcion..."
                                )
                            )
                        }
                    }
                }
            }

            items(uiState.products, key = { it.productId }) { product ->
                SubscriptionPlanCard(
                    product = product,
                    isActive = uiState.activeProductId == product.productId,
                    onSubscribe = {
                        if (activity != null) {
                            viewModel.purchase(activity, product.productId)
                        }
                    }
                )
            }

            if (uiState.products.isEmpty() && !uiState.isLoading) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = dhGlassCardColors()
                    ) {
                        Text(
                            text = tr(
                                "No subscription plans found in Play Billing yet.",
                                "Aun no hay planes cargados en Play Billing."
                            ),
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }

    val billingError = mapBillingError(uiState.errorCode, uiState.errorMessage)
    val actionError = mapActionError(uiState.actionErrorCode)
    val visibleError = actionError ?: billingError
    if (visibleError != null) {
        AlertDialog(
            onDismissRequest = viewModel::clearErrors,
            title = { Text(tr("Monetization", "Monetizacion")) },
            text = { Text(visibleError) },
            confirmButton = {
                TextButton(onClick = viewModel::clearErrors) {
                    Text(tr("OK", "Aceptar"))
                }
            }
        )
    }
}

@Composable
private fun ProValueCard(
    isPro: Boolean,
    activeProductId: String?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = dhGlassCardColors(emphasis = true)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = tr("Pro Benefits", "Beneficios Pro"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = tr(
                    "Unlock advanced comparisons, deeper analytics and future cloud backup.",
                    "Desbloquea comparaciones avanzadas, analitica profunda y futuro respaldo en nube."
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            val statusText = if (isPro) {
                tr(
                    "Active plan: ${planLabel(activeProductId)}",
                    "Plan activo: ${planLabel(activeProductId)}"
                )
            } else {
                tr("You are on Free plan", "Estas en el plan Free")
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun SubscriptionPlanCard(
    product: BillingSubscriptionProduct,
    isActive: Boolean,
    onSubscribe: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = dhGlassCardColors(emphasis = isActive)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = planLabel(product.productId),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = product.priceText,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (product.description.isNotBlank()) {
                Text(
                    text = product.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (product.hasFreeTrial) {
                Text(
                    text = tr("Includes trial period", "Incluye periodo de prueba"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !isActive,
                onClick = onSubscribe
            ) {
                Text(
                    if (isActive) {
                        tr("Current Plan", "Plan actual")
                    } else {
                        tr("Subscribe", "Suscribirme")
                    }
                )
            }
        }
    }
}

@Composable
private fun planLabel(productId: String?): String {
    return when (productId) {
        MonetizationCatalog.PRODUCT_PRO_YEARLY -> tr("Pro Yearly", "Pro Anual")
        MonetizationCatalog.PRODUCT_PRO_MONTHLY -> tr("Pro Monthly", "Pro Mensual")
        else -> tr("Free", "Free")
    }
}

@Composable
private fun mapBillingError(code: Int?, message: String?): String? {
    if (code == null && message.isNullOrBlank()) return null
    val generic = tr(
        "Play Billing error. Verify products in Play Console.",
        "Error de Play Billing. Verifica productos en Play Console."
    )
    return when (code) {
        3 -> tr("Billing unavailable on this device.", "Billing no disponible en este dispositivo.")
        5 -> tr("Developer error in Billing setup.", "Error de desarrollador en Billing.")
        7 -> tr("Item already owned.", "El item ya esta activo.")
        else -> if (message.isNullOrBlank()) generic else "$generic\n($message)"
    }
}

@Composable
private fun mapActionError(code: String?): String? {
    return when (code) {
        "BILLING_NOT_READY" -> tr("Billing is not ready yet.", "Billing aun no esta listo.")
        "PRODUCT_NOT_LOADED" -> tr("Product not loaded from Play Billing.", "Producto no cargado desde Play Billing.")
        "OFFER_TOKEN_NOT_FOUND" -> tr("Offer token not available for this product.", "No hay offer token para este producto.")
        else -> if (code == null) null else tr("Could not start purchase flow.", "No se pudo iniciar la compra.")
    }
}

