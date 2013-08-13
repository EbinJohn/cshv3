#!/bin/bash
export EnableNuGetPackageRestore=true
wget http://nuget.org/nuget.exe
mv nuget.exe ./DotNet/ServerResource/.nuget/NuGet.exe
chmod a+x ./DotNet/ServerResource/.nuget/NuGet.exe
xbuild /p:Configuration="NoUnitTests" /p:BuildWithMono="true" ./DotNet/ServerResource/ServerResource.sln
