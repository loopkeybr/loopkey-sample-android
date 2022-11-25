package br.com.loopkey.sample

import android.Manifest
import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.MaterialTheme.typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import br.com.loopkey.sample.lk.LKDeviceType
import br.com.loopkey.sample.lk.LKScanResult
import br.com.loopkey.sample.ui.theme.ComposableSampleTheme
import com.google.accompanist.permissions.*

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        this.viewModel = MainViewModel(applicationContext)

        setContent {
            ComposableSampleTheme {
                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
                    Content(this.viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun Content(viewModel: MainViewModel)
{
    val bluetoothPermissions = mutableListOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)

    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
        bluetoothPermissions.add(Manifest.permission.BLUETOOTH)
        bluetoothPermissions.add(Manifest.permission.BLUETOOTH_ADMIN)
    } else {
        bluetoothPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
        bluetoothPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
    }

    val multiplePermissionState = rememberMultiplePermissionsState(
        permissions = bluetoothPermissions
    )

    if (multiplePermissionState.allPermissionsGranted) {
        viewModel.onPermissionsGranted()
        renderDevicesList(viewModel)
    } else {
        requestPermissions(viewModel = viewModel, multiplePermissionState)
    }
}


@OptIn(ExperimentalPermissionsApi::class)
@Composable fun requestPermissions(viewModel: MainViewModel, permissionState: MultiplePermissionsState)
{
    PermissionsRequired(
        multiplePermissionsState = permissionState,
        permissionsNotGrantedContent = { Log.d("Permission", "Permissions should be granted to get this sample working.")},
        permissionsNotAvailableContent = { /* ... */ }
    ) {
        viewModel.onPermissionsGranted()
    }

    Row {
        Text("Permissions should be granted to get this sample working.")
        TextButton(onClick = {
            permissionState.launchMultiplePermissionRequest()
        },) {
            Text(text = "Grant Permissions")
        }
    }
}

@Composable
fun renderDevicesList(viewModel: MainViewModel)
{
    val devices: List<LKScanResult> = remember  { viewModel.renderedDevices }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        items(devices, itemContent = { item ->
            VisibleDeviceItem(device = item, viewModel = viewModel)
        })
    }
}

@Composable
fun VisibleDeviceItem(device: LKScanResult, viewModel: MainViewModel)
{
    Card(
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .fillMaxWidth(),
        elevation = 2.dp,
        shape = RoundedCornerShape(corner = CornerSize(16.dp))

    ) {
        Row(horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.CenterVertically){
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(0.7f)
                    .align(Alignment.CenterVertically)) {
                Text(text = device.name ?: "Bluetooth Device", style = typography.h6)
                device.serial?.let {
                    Text(text = it, style = typography.caption)
                }
                device.macAddress?.let {
                    Text(text = it, style = typography.caption)
                }
            }

            TextButton(onClick = { viewModel.unlockTapped(device) }, colors = ButtonDefaults.buttonColors(backgroundColor = Color.Transparent)) {
                Text(text = "Unlock")
            }
        }
    }
}
@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    val lkDevice = LKScanResult(
        0,
        LKDeviceType.LOOPKEY,
        "LKDevice",
        false,
        "LLKFAACAAACAAHFY",
        "1.3.0",
        false,
        null,
        null,
        null,
        0,
        null,
        null,
        false)
    val ttDevice =  LKScanResult(
        0,
        LKDeviceType.TTLOCK,
        "TTDevice",
        false,
        null,
        "1.3.0",
        false,
        "6B:D3:5F:B5:5C:AA",
        null,
        null,
        0,
        null,
        null,
        false)

    ComposableSampleTheme {
        Content(MainViewModel(LocalContext.current))
    }
}