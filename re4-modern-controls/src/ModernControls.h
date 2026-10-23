#pragma once
namespace re4t::init { void ModernControls(); }
class CameraQuasiFPS;
bool ModernControls_OwnsLocomotion();
void ModernControls_UpdateCamera(CameraQuasiFPS* camera);
bool ModernControls_UseAshleyShoulderCamera();
