namespace FlightTrackerAI.Web

open System
open System.IO
open System.Diagnostics
open clojure.lang

module Program =

    [<EntryPoint>]
    let main args =
        let port = if args.Length > 0 then args.[0] else "5121"
        let baseDir = AppContext.BaseDirectory
        let currentDir = Directory.GetCurrentDirectory()
        
        // Find repository root
        let repoRoot =
            if Directory.Exists(Path.Combine(currentDir, "src")) then currentDir
            elif Directory.Exists(Path.Combine(currentDir, "..", "..", "src")) then Path.GetFullPath(Path.Combine(currentDir, "..", ".."))
            else currentDir

        let loadPath = 
            $"{baseDir};{repoRoot}/src/FlightTrackerAI.Core;{repoRoot}/src/FlightTrackerAI.Infrastructure;{repoRoot}/src/FlightTrackerAI.Web"
        Environment.SetEnvironmentVariable("CLOJURE_LOAD_PATH", loadPath)

        printfn "=========================================================="
        printfn "  FlightTrackerAI Web Frontend & Server (.NET 10)"
        printfn "=========================================================="
        printfn "Starting ClojureCLR Web Application on http://localhost:%s/ ..." port

        // Launch browser
        try
            let url = sprintf "http://localhost:%s/" port
            Process.Start(ProcessStartInfo(FileName = url, UseShellExecute = true)) |> ignore
        with _ -> ()

        try
            RT.Init()
            let serverCljPath = Path.Combine(baseDir, "flight_tracker_ai", "web", "server.clj")
            Compiler.loadFile(serverCljPath) |> ignore
            let serverMain = RT.var("flight-tracker-ai.web.server", "-main")
            serverMain.invoke(port) |> ignore
            0
        with ex ->
            eprintfn "Fatal error running FlightTrackerAI Web: %s\n%s" ex.Message ex.StackTrace
            1
