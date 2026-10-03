# Prompts for a secret in a masked Windows dialog and stores it in server/secrets.properties
# (gitignored), so the value never passes through a chat transcript or the shell's output.
# Usage: powershell -File server/set-secret.ps1 -Key kinetic.api.key -Label "Kinetic Panel API key"
param(
    [Parameter(Mandatory = $true)][string]$Key,
    [string]$Label = $Key
)

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

$form = New-Object System.Windows.Forms.Form
$form.Text = "mc-plugins: $Label"
$form.Size = New-Object System.Drawing.Size(460, 170)
$form.StartPosition = 'CenterScreen'
$form.TopMost = $true
$form.FormBorderStyle = 'FixedDialog'
$form.MaximizeBox = $false

$text = New-Object System.Windows.Forms.Label
$text.Text = "Paste your $Label. It is saved only to server\secrets.properties."
$text.Location = New-Object System.Drawing.Point(12, 12)
$text.Size = New-Object System.Drawing.Size(420, 30)
$form.Controls.Add($text)

$box = New-Object System.Windows.Forms.TextBox
$box.UseSystemPasswordChar = $true
$box.Location = New-Object System.Drawing.Point(12, 48)
$box.Size = New-Object System.Drawing.Size(420, 24)
$form.Controls.Add($box)

$ok = New-Object System.Windows.Forms.Button
$ok.Text = 'Save'
$ok.Location = New-Object System.Drawing.Point(276, 86)
$ok.DialogResult = [System.Windows.Forms.DialogResult]::OK
$form.Controls.Add($ok)
$form.AcceptButton = $ok

$cancel = New-Object System.Windows.Forms.Button
$cancel.Text = 'Cancel'
$cancel.Location = New-Object System.Drawing.Point(357, 86)
$cancel.DialogResult = [System.Windows.Forms.DialogResult]::Cancel
$form.Controls.Add($cancel)
$form.CancelButton = $cancel

$form.Add_Shown({ $form.Activate(); $box.Focus() })
$result = $form.ShowDialog()
$value = $box.Text.Trim()
$form.Dispose()

if ($result -ne [System.Windows.Forms.DialogResult]::OK -or [string]::IsNullOrEmpty($value)) {
    Write-Output "cancelled, nothing saved"
    exit 1
}

$path = Join-Path $PSScriptRoot 'secrets.properties'
$lines = @()
if (Test-Path $path) {
    $lines = @([System.IO.File]::ReadAllLines($path) | Where-Object { $_ -notmatch "^\s*$([regex]::Escape($Key))\s*=" })
}
$lines += "$Key=$value"
# WriteAllText with UTF8Encoding($false): PowerShell 5.1's own writers prepend a BOM that breaks .properties parsing.
[System.IO.File]::WriteAllText($path, (($lines -join "`n") + "`n"), (New-Object System.Text.UTF8Encoding($false)))
Write-Output "saved $Key ($($value.Length) characters) to server\secrets.properties"
