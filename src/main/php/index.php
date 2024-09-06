<?php
$file = __FILE__;
require_once 'common.php';

if (! $user) {
    http_response_code(302);
    header("Location: login.php");
    die();
}


?>