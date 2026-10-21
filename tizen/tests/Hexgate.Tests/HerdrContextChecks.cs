using Hexgate.Core;

internal static class HerdrContextChecks
{
    public static void Run(Action<bool,string> check)
    {
        const string valid="""{"session":"fixture","automatic":true,"focused":"w1:p1","panes":[{"id":"w1:p1","cwd":"/tmp/fixture","name":"Own pane"}]}""";
        var context=HerdrContext.Parse(valid,"fixture");
        check(context.Automatic && context.Focused=="w1:p1" && context.Panes[0].Cwd=="/tmp/fixture","Herdr unique-client response resolves a listed pane");
        context=HerdrContext.Parse(valid.Replace("true","false"),"fixture");
        check(!context.Automatic && context.Focused==null && context.Panes.Count==1,"Herdr ambiguous response requires explicit pane choice");
        context=HerdrContext.Parse(valid.Replace("\"focused\":\"w1:p1\"","\"focused\":\"other\""),"fixture");
        check(!context.Automatic && context.Focused==null,"Herdr unknown focused pane cannot select automatically");
        foreach(var malformed in new[]{valid.Replace("fixture\",\"automatic","other\",\"automatic"),valid.Replace("/tmp/fixture","relative"),"{\"error\":\"unavailable\"}"}) {
            bool rejected=false;try{HerdrContext.Parse(malformed,"fixture");}catch(Exception){rejected=true;}
            check(rejected,"Herdr invalid session/path/error response is rejected");
        }
        bool invalid=false;try{HerdrContext.Command("print('safe')","fixture;id","/dev/pts/1");}catch(ArgumentException){invalid=true;}
        check(invalid,"Herdr session input cannot inject a command");
        invalid=false;try{HerdrContext.Command("print('safe')","fixture","/dev/pts/1;id");}catch(ArgumentException){invalid=true;}
        check(invalid,"Herdr TTY input cannot inject a command");
        string command=HerdrContext.Command("print('safe')","fixture","/dev/pts/1");
        check(command.StartsWith("exec python3 -c '") && command.Contains("'\"'\"'") && command.EndsWith("'/dev/pts/1'\n"),"Herdr helper uses literal shell quoting and an explicit client identity");
        invalid=false;try{HerdrContext.Parse(new string(' ',HerdrContext.OutputLimit+1),"fixture");}catch(InvalidDataException){invalid=true;}
        check(invalid,"Herdr response byte limit is enforced before JSON parsing");
    }
}
