$apiV1 = "root\virtualization"
$HyperVServer = "localhost"
$VMName = $args[0]

$vmList = Get-CimInstance -namespace $apiV1 -class MSVM_ComputerSystem -filter "ElementName='$VMName'"

# Assume one and only one match
$vm = $vmList[0]

$settings = Get-CimAssociatedInstance -CimInstance $vm `
                            -Association Msvm_SettingsDefineState `
                            -ResultClassName Msvm_VirtualSystemSettingData 

# Aiming for root\virtualization:Msvm_VirtualSystemManagementService.GetSummaryInformation($settings, )
$vmSysMans = Get-CimInstance -namespace $apiV1 -class Msvm_VirtualSystemManagementService
$reqInfo = 0, 1, 2
#$refVar = "foo", "bar"
Invoke-CimMethod -InputObject $vmSysMans -MethodName GetSummaryInformation `
    -Arguments @{SettingData = $settings; RequestedInformation = $reqInfo;  SummaryInformation = $refVar}
    