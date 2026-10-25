//! PolyModLoader-<version>.exe: runs the Poly Mod Loader installer jar that is built into this program.
//!
//! Players rarely have Java installed, but the Minecraft Launcher downloads its own Java runtime. This
//! program looks for a Java runtime (17 or newer) in this order and runs the installer with it:
//!
//! 1. `JAVA_HOME`
//! 2. the runtimes of the Minecraft Launcher (`%APPDATA%\.minecraft\runtime` and the Microsoft Store
//!    launcher's `%LOCALAPPDATA%\Packages\Microsoft.4297127D64EC6_8wekyb3d8bbwe\LocalCache\Local\runtime`),
//!    newest first
//! 3. `javaw.exe` on `PATH`
//!
//! The jar is written to the temporary folder, run, and deleted afterwards. Arguments are passed on, so
//! `PolyModLoader-<version>.exe install --dir <folder>` works as well (without console output).
#![windows_subsystem = "windows"]

use std::env;
use std::ffi::OsStr;
use std::fs;
use std::os::windows::ffi::OsStrExt;
use std::path::{Path, PathBuf};
use std::process::{self, Command};

static INSTALLER_JAR: &[u8] = include_bytes!(env!("POLY_INSTALLER_JAR"));
const POLY_VERSION: &str = env!("POLY_VERSION");
const MINIMUM_JAVA: u32 = 17;
const STORE_LAUNCHER_RUNTIME: &str =
    r"Packages\Microsoft.4297127D64EC6_8wekyb3d8bbwe\LocalCache\Local\runtime";

fn main() {
    let java = match find_java() {
        Some(java) => java,
        None => {
            message_box(
                &format!(
                    "Java {MINIMUM_JAVA} 이상을 찾지 못했습니다.\n\
                     Minecraft Launcher에서 Minecraft를 한 번 실행한 뒤 다시 시도하거나, Java를 설치하세요.\n\n\
                     No Java {MINIMUM_JAVA} or newer was found.\n\
                     Play Minecraft once from the Minecraft Launcher and try again, or install Java."
                ),
                true,
            );
            process::exit(1);
        }
    };
    let jar = env::temp_dir().join(format!("PolyModLoader-{POLY_VERSION}-installer-{}.jar", process::id()));
    if let Err(error) = fs::write(&jar, INSTALLER_JAR) {
        message_box(&format!("임시 파일을 쓰지 못했습니다 / Could not write {}: {error}", jar.display()), true);
        process::exit(1);
    }
    let status = Command::new(&java).arg("-jar").arg(&jar).args(env::args_os().skip(1)).status();
    let _ = fs::remove_file(&jar);
    match status {
        Ok(status) => process::exit(status.code().unwrap_or(1)),
        Err(error) => {
            message_box(&format!("Java를 실행하지 못했습니다 / Could not start {}: {error}", java.display()), true);
            process::exit(1);
        }
    }
}

/// The first usable `javaw.exe`, see the module comment for the order.
fn find_java() -> Option<PathBuf> {
    if let Some(home) = env::var_os("JAVA_HOME") {
        let java = Path::new(&home).join("bin").join("javaw.exe");
        if java_major(&java).is_some_and(|major| major >= MINIMUM_JAVA) {
            return Some(java);
        }
    }
    let mut runtimes: Vec<(u32, PathBuf)> = Vec::new();
    let mut roots: Vec<PathBuf> = Vec::new();
    if let Some(app_data) = env::var_os("APPDATA") {
        roots.push(Path::new(&app_data).join(".minecraft").join("runtime"));
    }
    if let Some(local) = env::var_os("LOCALAPPDATA") {
        roots.push(Path::new(&local).join(STORE_LAUNCHER_RUNTIME));
    }
    // <root>\<component>\<platform>\<component>\bin\javaw.exe, such as
    // runtime\java-runtime-epsilon\windows-x64\java-runtime-epsilon\bin\javaw.exe
    for root in roots {
        for component in subdirectories(&root) {
            for platform in subdirectories(&component) {
                for runtime in subdirectories(&platform) {
                    let java = runtime.join("bin").join("javaw.exe");
                    if let Some(major) = java_major(&java) {
                        runtimes.push((major, java));
                    }
                }
            }
        }
    }
    runtimes.sort_by(|a, b| b.0.cmp(&a.0));
    if let Some((major, java)) = runtimes.into_iter().next() {
        if major >= MINIMUM_JAVA {
            return Some(java);
        }
    }
    let path = env::var_os("PATH")?;
    env::split_paths(&path)
        .map(|directory| directory.join("javaw.exe"))
        .find(|java| java_major(java).is_some_and(|major| major >= MINIMUM_JAVA))
}

fn subdirectories(directory: &Path) -> Vec<PathBuf> {
    match fs::read_dir(directory) {
        Ok(entries) => entries
            .filter_map(Result::ok)
            .map(|entry| entry.path())
            .filter(|path| path.is_dir())
            .collect(),
        Err(_) => Vec::new(),
    }
}

/// The major version of the runtime `java` belongs to: from its `release` file, or else from `java -version`.
fn java_major(java: &Path) -> Option<u32> {
    if !java.is_file() {
        return None;
    }
    let home = java.parent()?.parent()?;
    if let Ok(release) = fs::read_to_string(home.join("release")) {
        for line in release.lines() {
            if let Some(value) = line.strip_prefix("JAVA_VERSION=") {
                return parse_major(value.trim_matches('"'));
            }
        }
    }
    // Launchers such as Oracle's javapath have no release file next to them; ask the runtime itself.
    let console = java.with_file_name("java.exe");
    let output = Command::new(if console.is_file() { console } else { java.to_path_buf() })
        .arg("-version")
        .output()
        .ok()?;
    let text = String::from_utf8_lossy(&output.stderr);
    let quoted = text.split('"').nth(1)?;
    parse_major(quoted)
}

/// `25.0.3` -> 25, `1.8.0_301` -> 8
fn parse_major(version: &str) -> Option<u32> {
    let mut parts = version.split(['.', '_', '-', '+']);
    let first: u32 = parts.next()?.parse().ok()?;
    if first == 1 {
        parts.next()?.parse().ok()
    } else {
        Some(first)
    }
}

#[link(name = "user32")]
extern "system" {
    fn MessageBoxW(window: *mut core::ffi::c_void, text: *const u16, caption: *const u16, kind: u32) -> i32;
}

fn message_box(text: &str, error: bool) {
    const MB_ICONERROR: u32 = 0x10;
    const MB_ICONINFORMATION: u32 = 0x40;
    let text = wide(text);
    let caption = wide(&format!("Poly Mod Loader {POLY_VERSION}"));
    // SAFETY: both strings are NUL-terminated UTF-16 buffers that live until the call returns.
    unsafe {
        MessageBoxW(
            core::ptr::null_mut(),
            text.as_ptr(),
            caption.as_ptr(),
            if error { MB_ICONERROR } else { MB_ICONINFORMATION },
        );
    }
}

fn wide(text: &str) -> Vec<u16> {
    OsStr::new(text).encode_wide().chain(std::iter::once(0)).collect()
}

#[cfg(test)]
mod tests {
    use super::parse_major;

    #[test]
    fn reads_major_versions() {
        assert_eq!(parse_major("25.0.3"), Some(25));
        assert_eq!(parse_major("21"), Some(21));
        assert_eq!(parse_major("17.0.12+7"), Some(17));
        assert_eq!(parse_major("1.8.0_301"), Some(8));
        assert_eq!(parse_major("abc"), None);
    }
}
