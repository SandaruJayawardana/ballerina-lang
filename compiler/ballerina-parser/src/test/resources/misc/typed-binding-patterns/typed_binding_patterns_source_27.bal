public function testListBindingPatternWithInvalidMember() {
    Arr [x, jwt:Payload] = [];
    Arr [jwt:Header, y] = [];
    Arr2 [jwt:Header, z, jwt:Payload] = [];
    Arr2 [jwt:Header,  jwt:Payload, jwt:Msg] = [];
}
