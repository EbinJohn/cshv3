# Concept from http://blogs.msdn.com/b/virtual_pc_guy/archive/2008/05/28/scripting-vm-creation-with-hyper-v.aspx
#
$HyperVServer = "localhost"
$VMName = $args[0]
$VMMemory = 1024


# Create copy of VHDX for machine
$NewVHD = "E:\Disks\Disks\$VMName.vhdx"
Copy-Item "E:\Disks\Disks\SampleHyperVCentOS63VM.vhdx" $NewVHD

# v1 of interface stores VM details in MSVM_VirtualSystemGlobalSettingData object
$wmiClassString = "\\" + $HyperVServer + "\root\virtualization:Msvm_VirtualSystemGlobalSettingData"
$wmiClass = [WMIClass]$wmiClassString
$newVSGlobalSettingData = $wmiClass.CreateInstance()

# wait for the new object to be populated
while ($newVSGlobalSettingData.psbase.Properties -eq $null)
    { start-sleep 1 }

# Set VM properties
$newVSGlobalSettingData.psbase.Properties.Item("ElementName").value = $VMName

# Create VM using the VirtualSystemManagementService object
$VSManagementService = gwmi MSVM_VirtualSystemManagementService -namespace "root\virtualization" -computername $HyperVServer
$result = $VSManagementService.DefineVirtualSystem($newVSGlobalSettingData.psbase.GetText(1))

#Return success if the return value is "0"
if ($Result.ReturnValue -eq 0)
   {write-host "Virtual machine created."} 

#If the return value is "4096" then the operation failed
ElseIf ($Result.ReturnValue -ne 4096)
   {write-host "Failed to create virtual machine"
   exit}

Else
   {#wait for completion if the call resulted in a job
    $job=[WMI]$Result.job

    # a jobstate of "3" is 'starting', of "4" means 'running'
    while ($job.JobState -eq 3 -or $job.JobState -eq 4)
      {write-host $job.PercentComplete
       start-sleep 1

       #Refresh the job object
       $job=[WMI]$Result.job}

    #A jobstate of "7" means success
    if ($job.JobState -eq 7)
       {write-host "Virtual machine created."}
    Else
       {write-host "Failed to create virtual machine"
        write-host "ErrorCode:" $job.ErrorCode
        write-host "ErrorDescription" $job.ErrorDescription
        exit
        }
    }

# Attach the vhdx to the VM and start

Add-VMHardDiskDrive -VMName $VMName -path $NewVHD
Start-vm $VMName 


return $Result
