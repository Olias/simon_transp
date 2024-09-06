<?php
$file = __FILE__;
require_once 'common.php';

if ($user) {
    http_response_code(302);
    header("Location: index.php");
    die();
}

if ( isset($_GET['basicauth']) ){
    header('WWW-Authenticate: Basic realm="Simon Transporte"');
}
$username = null;
if (($_SERVER['REQUEST_METHOD'] && isset($_POST['login'])) || // http post
(isset($_SERVER['PHP_AUTH_USER']) && isset($_SERVER['PHP_AUTH_PW']))) // basic auth
{
    
    function login()
    {
        global $mysqli;
        global $SID;
        global $remote_ip;
        global $username;
        
        if (isset($_SERVER['PHP_AUTH_USER'])){
            $username = $_SERVER['PHP_AUTH_USER'];
            $pass = $_SERVER['PHP_AUTH_PW'];
        }else{
            $username = filter_input(INPUT_POST, 'user', FILTER_UNSAFE_RAW);
            $pass = filter_input(INPUT_POST, 'pw', FILTER_UNSAFE_RAW);
        }

       
        $stmt = $mysqli->prepare("SELECT * FROM Login WHERE username = ?");
        $stmt->bind_param('s', $username);
        $stmt->execute() or die("sql error");
        $res = $stmt->get_result();
        if (! $res->num_rows) {
            return false;
        } else {
            $user = $res->fetch_object();
            $res->free_result();
            
            $ok = password_verify($pass . $user->salt, $user->password);
            if (! $ok) {
                return false;
            }

            unset($user->password);
            unset($user->salt);

            $stmt = $mysqli->prepare("UPDATE Login SET lastLogin = NOW(),remoteIp = ? , sessionId = ? WHERE username = ? and id = ? LIMIT 1");
            $stmt->bind_param('sssd', $remote_ip, $SID, $username, $user->id);
            $stmt->execute() or die("sql error");
            return $user;
        }
    }

    $user = login();
    $error= false;
    if (!$user) {
        $error= true;
        http_response_code(401);
        if (isset($_SERVER['PHP_AUTH_USER'])){
            header('WWW-Authenticate: Basic realm="Simon Transporte"');
        }
    }
}

build_head("Anmelden", "login");
if ( $error){
    ?><h1 class="error" >Falsche Zugangsdaten?</h1><?php 
}
?>
<form method="post">
	<input placeholder="Nutzername" name="user" /> <input
		placeholder="Password" type="password" name="pw"> <input name="login"
		type="hidden">
	<button>Anmelden</button>
</form>

<?php
build_foot();