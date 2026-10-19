[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [Parameter(Mandatory = $true)]
    [string] $ListenAddress,
    [ValidateRange(1, 65535)]
    [int] $ListenPort = 2222,
    [ValidateRange(1, 65535)]
    [int] $SshPort = 22,
    [string] $Distribution = 'Ubuntu'
)
$ErrorActionPreference = 'Stop'
$HexgateParsedAddress = $null
if (-not [System.Net.IPAddress]::TryParse($ListenAddress, [ref] $HexgateParsedAddress) -or
    $HexgateParsedAddress.AddressFamily -ne [System.Net.Sockets.AddressFamily]::InterNetwork) {
    throw 'ListenAddress must be an explicit Windows IPv4 address.'
}
$HexgateAddresses = (& wsl.exe -d $Distribution -- hostname -I).Trim() -split '\s+'
if ($LASTEXITCODE -ne 0) { throw 'Cannot query the selected WSL distribution.' }
$HexgateTarget = $HexgateAddresses | Where-Object { $_ -match '^\d+\.\d+\.\d+\.\d+$' } | Select-Object -First 1
if (-not $HexgateTarget) { throw 'No WSL IPv4 address found. Check NAT/mirrored networking configuration.' }
$HexgateDescription = "${ListenAddress}:${ListenPort} -> ${HexgateTarget}:${SshPort} ($Distribution)"
Write-Output $HexgateDescription
if ($PSCmdlet.ShouldProcess($HexgateDescription, 'Create or update the Windows TCP port proxy')) {
    & netsh.exe interface portproxy set v4tov4 "listenaddress=$ListenAddress" "listenport=$ListenPort" "connectaddress=$HexgateTarget" "connectport=$SshPort"
    if ($LASTEXITCODE -ne 0) {
        & netsh.exe interface portproxy add v4tov4 "listenaddress=$ListenAddress" "listenport=$ListenPort" "connectaddress=$HexgateTarget" "connectport=$SshPort"
        if ($LASTEXITCODE -ne 0) { throw 'Port proxy update failed. Run from an elevated Windows PowerShell.' }
    }
}
