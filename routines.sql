create
    definer = root@localhost procedure getInvitations()
BEGIN
    select * from invitation;
END;

create
    definer = root@localhost procedure getcountinvitations(IN apiid int, OUT cnt int)
BEGIN
    select count(*) into cnt from invitation where createdon = curdate() and api_id = apiid;
END;

create
    definer = root@localhost procedure invite(IN user_id int, IN phones varchar(35))
begin
    INSERT INTO invitation (author, phone, createdon, api_id) VALUES (user_id, phones, curdate(), 4);
end;
