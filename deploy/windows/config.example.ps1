# NutriAI - Windows local configuration
# Copy this file to config.ps1 and edit values. config.ps1 is gitignored.
$NutriAI = @{
    ProjectRoot = 'D:\Web_JavaWork\NutriAI'
    JavaHome    = 'D:\java\21'
    JarPath     = 'D:\Web_JavaWork\NutriAI\backend\target\nutri-ai-0.1.0.jar'
    Port        = 8080
    Profile     = 'prod'

    MysqlHost     = 'localhost'
    MysqlPort     = 3306
    MysqlDb       = 'nutriai'
    MysqlUser     = 'root'
    MysqlPassword = 'bingxin'
    MysqlDump     = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqldump.exe'

    AiApiKey    = ''
    AiEndpoint  = 'https://api.deepseek.com/v1'
    AiModel     = 'deepseek-chat'
    BaseUrl     = 'http://localhost:8080'
    LogLevel    = 'INFO'

    BackupRetentionDays = 14
}
