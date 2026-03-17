use serde::Serialize;

#[tauri::command]
fn greet(name: &str) -> String {
    format!("Hello, {}! Welcome to SquadX Desktop.", name)
}

#[tauri::command]
fn get_app_version() -> String {
    env!("CARGO_PKG_VERSION").to_string()
}

#[derive(Serialize)]
pub struct SystemInfo {
    os: String,
    arch: String,
    memory_mb: u64,
}

#[tauri::command]
fn get_system_info() -> SystemInfo {
    SystemInfo {
        os: std::env::consts::OS.to_string(),
        arch: std::env::consts::ARCH.to_string(),
        memory_mb: get_total_memory_mb(),
    }
}

fn get_total_memory_mb() -> u64 {
    // Return 0 as a fallback; a full implementation would use sysinfo crate
    // This avoids adding heavy dependencies for a scaffold
    0
}

pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_shell::init())
        .invoke_handler(tauri::generate_handler![
            greet,
            get_app_version,
            get_system_info,
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
