<?php
if (! isset($file)) {
    http_response_code(403);
    die();
}
session_set_cookie_params(24*3600, // 24h
    null, // path
    null, // domain
    null, // secure
    true // http only
    );
session_start();
$SID = session_id();


if (!empty($_SERVER['HTTP_CLIENT_IP'])) {
    $remote_ip = $_SERVER['HTTP_CLIENT_IP'];
} elseif (!empty($_SERVER['HTTP_X_FORWARDED_FOR'])) {
    $remote_ip = $_SERVER['HTTP_X_FORWARDED_FOR'];
} else {
    $remote_ip = $_SERVER['REMOTE_ADDR'];
}

if (!isset($user) or !$mysqli) {

    function connect_db()
    {
        $server = 'localhost';
        $user = 'simon';
        $pass = 'Aepee9Uo cha5Mohm';
        $database = 'simon';

        $mysqli = new mysqli($server, $user, $pass, $database);
        return $mysqli;
    }
    
    $mysqli = connect_db();
}

if (!isset($user) or ! $user) {  
    $stmt = $mysqli->prepare("SELECT * FROM Login WHERE sessionId = ? and remoteIp = ? and lastAction > NOW()-INTERVAL 6 HOUR and lastLogin > NOW()-Interval 24 HOUR");
    
    $stmt->bind_param('ss', $SID, $remote_ip);

    $stmt->execute() or die("sql error");
    $res = $stmt->get_result();

    if (! $res->num_rows) {
        $user = false;
    } else {
        $user = $res->fetch_object();
        $res->free_result();
        unset ($user->password);
        unset ($user->salt);
    }
}


function build_head($title, $bodyclass){
    ?><!DOCTYPE HTML>
    <html>
    <head>
    <title><?=$title;?></title>
    <link rel="stylesheet" href="static/style.css">
    </head>
    <body class="<?=$bodyclass;?>">
    <?php 
}

function build_foot(){
    ?>
    </body>
    </html>
    <?php 
}

