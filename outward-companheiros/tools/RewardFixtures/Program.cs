using System;
using System.IO;
using System.Linq;
using Mono.Cecil;

if (args.Length!=3) throw new ArgumentException("<reference Assembly-CSharp.dll> <temporary fixtures directory> <Unity reference directory>");
using var resolver = new DefaultAssemblyResolver();
resolver.AddSearchDirectory(Path.GetDirectoryName(args[0]));
resolver.AddSearchDirectory(args[2]);
// TEST ONLY: alter copies of public development metadata, never game files.
foreach (string scenario in new[] { "enum-changed", "private-selector" })
{
    string directory=Path.Combine(args[1],scenario);
    Directory.CreateDirectory(directory);
    foreach(string path in Directory.GetFiles(Path.GetDirectoryName(args[0]),"*.dll"))
        File.Copy(path,Path.Combine(directory,Path.GetFileName(path)),true);
    using var game=AssemblyDefinition.ReadAssembly(args[0], new ReaderParameters { AssemblyResolver=resolver });
    var type=game.MainModule.GetType("NodeCanvas.Tasks.Actions.GiveReward");
    if(scenario=="enum-changed")
        type.NestedTypes.Single(t=>t.Name=="Receiver").Fields.Single(f=>f.Name=="Everyone").Constant=3;
    else {
        var selector=type.Fields.Single(f=>f.Name=="RewardReceiver");
        selector.Attributes=(selector.Attributes&~FieldAttributes.FieldAccessMask)|FieldAttributes.Private;
    }
    game.Write(Path.Combine(directory,"Assembly-CSharp.dll"));
    Console.WriteLine("Generated TEMPORARY incompatible metadata fixture: "+scenario);
}
