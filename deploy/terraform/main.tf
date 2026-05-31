terraform {
  required_version = ">= 1.5.0"

  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 4.10"
    }
  }

  backend "azurerm" {
    resource_group_name  = "tfstate-rg"
    storage_account_name = "foundflowtfstate720ff3"
    container_name       = "tfstate"
    key                  = "azure-vm.tfstate"
    use_azuread_auth     = true
  }
}

provider "azurerm" {
  features {}
  subscription_id = var.subscription_id
}

variable "subscription_id" {
  description = "Azure subscription ID. Run `az account show --query id -o tsv` to find yours."
  type        = string
}

variable "resource_group_name" {
  description = "Name of the resource group to create."
  type        = string
  default     = "foundflow-rg"
}

variable "location" {
  description = "Azure region for all resources. Constrained to the 5 Azure-for-Students-allowed regions; austriaeast has had the best capacity for B-v2 SKUs in recent tests."
  type        = string
  default     = "austriaeast"
}

variable "vm_name" {
  description = "Name of the VM (and prefix for related resources)."
  type        = string
  default     = "foundflow-vm"
}

variable "vm_size" {
  description = "Azure VM size. Standard_B2s_v2 = 2 vCPU / 8 GB / ~$0.05/hr — proven to deploy under the Azure-for-Students subscription. Bump to Standard_B4ms (4 vCPU / 16 GB) if you hit OOM."
  type        = string
  default     = "Standard_B2s_v2"
}

variable "admin_username" {
  description = "Linux admin user on the VM."
  type        = string
  default     = "azureuser"
}

variable "ssh_public_key_path" {
  description = "Path to the SSH public key to authorize on the VM."
  type        = string
  default     = "~/.ssh/tum_devops_azure.pub"
}

variable "ssh_allowed_cidr" {
  description = "CIDR allowed to reach SSH (port 22)."
  type        = string
  # TODO: tighten this to your home/office IP, e.g. "203.0.113.42/32".
  # 0.0.0.0/0 is convenient for a course project but exposes SSH to the world.
  default = "0.0.0.0/0"
}

variable "os_disk_size_gb" {
  description = "OS disk size in GB."
  type        = number
  default     = 30
}

variable "vm_image_sku" {
  description = "Ubuntu 22.04 image SKU. Use `22_04-lts-gen2` for x86 VMs (B/D/E-series) or `22_04-lts-arm64` for ARM VMs (D*ps_v6, E*ps_v6)."
  type        = string
  default     = "22_04-lts-gen2"
}

# -----------------------------------------------------------------------------

resource "azurerm_resource_group" "this" {
  name     = var.resource_group_name
  location = var.location
}

resource "azurerm_virtual_network" "this" {
  name                = "${var.vm_name}-vnet"
  address_space       = ["10.0.0.0/16"]
  location            = azurerm_resource_group.this.location
  resource_group_name = azurerm_resource_group.this.name
}

resource "azurerm_subnet" "this" {
  name                 = "${var.vm_name}-subnet"
  resource_group_name  = azurerm_resource_group.this.name
  virtual_network_name = azurerm_virtual_network.this.name
  address_prefixes     = ["10.0.1.0/24"]
}

resource "azurerm_network_security_group" "this" {
  name                = "${var.vm_name}-nsg"
  location            = azurerm_resource_group.this.location
  resource_group_name = azurerm_resource_group.this.name

  security_rule {
    name                       = "allow-ssh"
    priority                   = 1001
    direction                  = "Inbound"
    access                     = "Allow"
    protocol                   = "Tcp"
    source_port_range          = "*"
    destination_port_range     = "22"
    source_address_prefix      = var.ssh_allowed_cidr
    destination_address_prefix = "*"
  }

  security_rule {
    name                       = "allow-http"
    priority                   = 1010
    direction                  = "Inbound"
    access                     = "Allow"
    protocol                   = "Tcp"
    source_port_range          = "*"
    destination_port_range     = "80"
    source_address_prefix      = "*"
    destination_address_prefix = "*"
  }

  security_rule {
    name                       = "allow-https"
    priority                   = 1020
    direction                  = "Inbound"
    access                     = "Allow"
    protocol                   = "Tcp"
    source_port_range          = "*"
    destination_port_range     = "443"
    source_address_prefix      = "*"
    destination_address_prefix = "*"
  }
}

resource "azurerm_subnet_network_security_group_association" "this" {
  subnet_id                 = azurerm_subnet.this.id
  network_security_group_id = azurerm_network_security_group.this.id
}

resource "azurerm_public_ip" "this" {
  name                = "${var.vm_name}-pip"
  location            = azurerm_resource_group.this.location
  resource_group_name = azurerm_resource_group.this.name
  allocation_method   = "Static"
  sku                 = "Standard"
}

resource "azurerm_network_interface" "this" {
  name                = "${var.vm_name}-nic"
  location            = azurerm_resource_group.this.location
  resource_group_name = azurerm_resource_group.this.name

  ip_configuration {
    name                          = "internal"
    subnet_id                     = azurerm_subnet.this.id
    private_ip_address_allocation = "Dynamic"
    public_ip_address_id          = azurerm_public_ip.this.id
  }
}

resource "azurerm_linux_virtual_machine" "this" {
  name                  = var.vm_name
  resource_group_name   = azurerm_resource_group.this.name
  location              = azurerm_resource_group.this.location
  size                  = var.vm_size
  admin_username        = var.admin_username
  network_interface_ids = [azurerm_network_interface.this.id]

  admin_ssh_key {
    username   = var.admin_username
    public_key = file(pathexpand(var.ssh_public_key_path))
  }

  os_disk {
    caching              = "ReadWrite"
    storage_account_type = "Standard_LRS"
    disk_size_gb         = var.os_disk_size_gb
  }

  source_image_reference {
    publisher = "Canonical"
    offer     = "0001-com-ubuntu-server-jammy"
    sku       = var.vm_image_sku
    version   = "latest"
  }
}

# -----------------------------------------------------------------------------

output "public_ip" {
  description = "Public IP of the VM."
  value       = azurerm_public_ip.this.ip_address
}

output "ssh_command" {
  description = "Ready-to-run SSH command for the VM."
  value       = "ssh -i ~/.ssh/tum_devops_azure ${var.admin_username}@${azurerm_public_ip.this.ip_address}"
}
