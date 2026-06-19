grammar SMI;

// SMIv1 / SMIv2 ASN.1 MIB grammar, covering pysmi's SmiV1CompatParser /
// SmiV2Parser construct set. Single combined grammar: SMIv1-only tokens
// (TRAP_TYPE, ACCESS) are accepted alongside SMIv2 tokens (NOTIFICATION_TYPE,
// MAX_ACCESS) so one grammar parses both dialects; normalisation to the
// SMIv2 JSON shape happens in the JSON generator pass, not here.

// ---------------------------------------------------------------------
// Parser rules
// ---------------------------------------------------------------------

mibFile
    : moduleDefinition+ EOF
    ;

moduleDefinition
    : moduleName DEFINITIONS ASSIGN_OP BEGIN_
      linkagePart?
      moduleBody
      END_
    ;

moduleName
    : Identifier_UpperStart
    ;

linkagePart
    : exportsPart? importsPart?
    ;

exportsPart
    : EXPORTS exportList? SEMI
    ;

exportList
    : identifierOrType (COMMA identifierOrType)*
    ;

importsPart
    : IMPORTS importList? SEMI
    ;

importList
    : importEntry+
    ;

importEntry
    : symbolList FROM moduleName
    ;

symbolList
    : identifierOrType (COMMA identifierOrType)*
    ;

// IMPORTS/EXPORTS symbol lists name macros (MODULE-IDENTITY, OBJECT-TYPE, ...)
// alongside ordinary identifiers. Those macro names lex as keyword tokens,
// not Identifier_*, so they need an explicit alternative here.
identifierOrType
    : identifier
    | MODULE_IDENTITY
    | OBJECT_IDENTITY
    | OBJECT_TYPE
    | NOTIFICATION_TYPE
    | TRAP_TYPE
    | TEXTUAL_CONVENTION
    | OBJECT_GROUP
    | NOTIFICATION_GROUP
    | MODULE_COMPLIANCE
    | AGENT_CAPABILITIES
    ;

moduleBody
    : assignment*
    ;

assignment
    : moduleIdentityClause
    | objectIdentityClause
    | objectTypeClause
    | notificationTypeClause
    | trapTypeClause
    | textualConventionClause
    | objectGroupClause
    | notificationGroupClause
    | moduleComplianceClause
    | agentCapabilitiesClause
    | macroClause
    | typeAssignment
    | valueAssignment
    ;

// --- MODULE-IDENTITY ---------------------------------------------------

moduleIdentityClause
    : identifier MODULE_IDENTITY
      LAST_UPDATED string_
      ORGANIZATION string_
      CONTACT_INFO string_
      DESCRIPTION string_
      revisionClause*
      ASSIGN_OP objectIdentifierValue
    ;

revisionClause
    : REVISION string_
      DESCRIPTION string_
    ;

// --- OBJECT-IDENTITY -----------------------------------------------------

objectIdentityClause
    : identifier OBJECT_IDENTITY
      STATUS status_
      DESCRIPTION string_
      referencePart?
      ASSIGN_OP objectIdentifierValue
    ;

// --- OBJECT-TYPE ---------------------------------------------------------

objectTypeClause
    : identifier OBJECT_TYPE
      SYNTAX syntax
      unitsPart?
      accessPart
      STATUS status_
      descriptionPart?
      referencePart?
      indexPart?
      defValPart?
      ASSIGN_OP objectIdentifierValue
    ;

unitsPart
    : UNITS string_
    ;

accessPart
    : MAX_ACCESS access_
    | ACCESS access_
    ;

descriptionPart
    : DESCRIPTION string_
    ;

referencePart
    : REFERENCE string_
    ;

indexPart
    : INDEX LBRACE indexTypes RBRACE
    | AUGMENTS LBRACE identifier RBRACE
    ;

indexTypes
    : indexType (COMMA indexType)*
    ;

indexType
    : IMPLIED? identifier
    ;

defValPart
    : DEFVAL LBRACE defValValue RBRACE
    ;

defValValue
    : value
    | LBRACE bitsValue? RBRACE
    | objectIdentifierValue
    ;

bitsValue
    : identifier (COMMA identifier)*
    ;

access_
    : NOT_ACCESSIBLE
    | ACCESSIBLE_FOR_NOTIFY
    | READ_ONLY
    | READ_WRITE
    | READ_CREATE
    | WRITE_ONLY
    ;

status_
    : CURRENT_
    | DEPRECATED
    | OBSOLETE
    | MANDATORY
    | OPTIONAL_
    ;

// --- NOTIFICATION-TYPE (SMIv2) -------------------------------------------

notificationTypeClause
    : identifier NOTIFICATION_TYPE
      objectsPart?
      STATUS status_
      DESCRIPTION string_
      referencePart?
      ASSIGN_OP objectIdentifierValue
    ;

objectsPart
    : OBJECTS LBRACE objectIdentifierList? RBRACE
    ;

objectIdentifierList
    : identifier (COMMA identifier)*
    ;

// --- TRAP-TYPE (SMIv1) -----------------------------------------------------

trapTypeClause
    : identifier TRAP_TYPE
      ENTERPRISE identifier
      variablesPart?
      DESCRIPTION string_
      referencePart?
      ASSIGN_OP Number_
    ;

variablesPart
    : VARIABLES LBRACE objectIdentifierList? RBRACE
    ;

// --- TEXTUAL-CONVENTION ----------------------------------------------------

textualConventionClause
    : identifier ASSIGN_OP TEXTUAL_CONVENTION
      displayPart?
      STATUS status_
      DESCRIPTION string_
      referencePart?
      SYNTAX syntax
    ;

displayPart
    : DISPLAY_HINT string_
    ;

// --- OBJECT-GROUP / NOTIFICATION-GROUP -------------------------------------

objectGroupClause
    : identifier OBJECT_GROUP
      OBJECTS LBRACE objectIdentifierList RBRACE
      STATUS status_
      DESCRIPTION string_
      referencePart?
      ASSIGN_OP objectIdentifierValue
    ;

notificationGroupClause
    : identifier NOTIFICATION_GROUP
      NOTIFICATIONS LBRACE objectIdentifierList RBRACE
      STATUS status_
      DESCRIPTION string_
      referencePart?
      ASSIGN_OP objectIdentifierValue
    ;

// --- MODULE-COMPLIANCE ------------------------------------------------------

moduleComplianceClause
    : identifier MODULE_COMPLIANCE
      STATUS status_
      DESCRIPTION string_
      referencePart?
      complianceModule+
      ASSIGN_OP objectIdentifierValue
    ;

complianceModule
    : MODULE moduleComplianceName?
      mandatoryGroupsPart?
      complianceGroupOrObject*
    ;

moduleComplianceName
    : identifier
    ;

mandatoryGroupsPart
    : MANDATORY_GROUPS LBRACE objectIdentifierList RBRACE
    ;

complianceGroupOrObject
    : GROUP identifier
      DESCRIPTION string_
    | OBJECT identifier
      syntaxOverridePart?
      writeSyntaxPart?
      accessOverridePart?
      DESCRIPTION string_
    ;

syntaxOverridePart
    : SYNTAX syntax
    ;

writeSyntaxPart
    : WRITE_SYNTAX syntax
    ;

accessOverridePart
    : MIN_ACCESS access_
    ;

// --- AGENT-CAPABILITIES ------------------------------------------------------

agentCapabilitiesClause
    : identifier AGENT_CAPABILITIES
      PRODUCT_RELEASE string_
      STATUS status_
      DESCRIPTION string_
      referencePart?
      capabilitiesModule*
      ASSIGN_OP objectIdentifierValue
    ;

capabilitiesModule
    : SUPPORTS identifier
      INCLUDES LBRACE objectIdentifierList RBRACE
      variationClause*
    ;

variationClause
    : VARIATION identifier
      syntaxOverridePart?
      writeSyntaxPart?
      accessOverridePart?
      creationPart?
      defValPart?
      DESCRIPTION string_
    ;

creationPart
    : CREATION_REQUIRES LBRACE objectIdentifierList RBRACE
    ;

// --- MACRO stub ------------------------------------------------------------

// The meta-MIBs that define these macros (SNMPv2-SMI, SNMPv2-CONF,
// SNMPv2-TC) name them with the same keyword tokens used elsewhere as
// construct introducers (e.g. "OBJECT-GROUP MACRO ::= BEGIN ... END"), not
// plain identifiers - identifierOrType already special-cases this for
// IMPORTS lists, same idea here.
macroClause
    : macroName MACRO ASSIGN_OP BEGIN_ .*? END_
    ;

macroName
    : identifier
    | MODULE_IDENTITY
    | OBJECT_IDENTITY
    | OBJECT_TYPE
    | NOTIFICATION_TYPE
    | TRAP_TYPE
    | TEXTUAL_CONVENTION
    | OBJECT_GROUP
    | NOTIFICATION_GROUP
    | MODULE_COMPLIANCE
    | AGENT_CAPABILITIES
    ;

// --- plain type / value assignments -----------------------------------------

typeAssignment
    : identifier ASSIGN_OP syntax
    ;

valueAssignment
    : identifier objectIdentifierType? ASSIGN_OP objectIdentifierValue
    ;

objectIdentifierType
    : OBJECT IDENTIFIER
    ;

// --- SYNTAX / type expressions ------------------------------------------------

syntax
    : typeReference constraintPart?           # plainType
    | SEQUENCE OF typeReference                # sequenceOfType
    | SEQUENCE LBRACE sequenceItems RBRACE      # sequenceType
    | BITS LBRACE namedBitList RBRACE           # bitsType
    | typeReference LBRACE namedNumberList RBRACE # enumeratedType
    ;

// Trailing comma before the closing brace is invalid strict ASN.1 but shows
// up in real vendor MIBs (e.g. Moxa mxMacsec.mib) - tolerated here.
sequenceItems
    : sequenceItem (COMMA sequenceItem)* COMMA?
    ;

// A constraintPart directly on a SEQUENCE field (e.g. "foo INTEGER (0..65535)")
// is common in real MIBs (RFC1213-MIB, RFC1271-MIB) even though it's
// redundant with the constraint on the OBJECT-TYPE that uses this field.
sequenceItem
    : identifier typeReference constraintPart?
    ;

// Trailing comma before the closing brace is invalid strict ASN.1 but shows
// up in real vendor MIBs (e.g. Moxa mxTimeSetting.mib) - tolerated here.
namedBitList
    : namedNumber (COMMA namedNumber)* COMMA?
    ;

namedNumberList
    : namedNumber (COMMA namedNumber)* COMMA?
    ;

namedNumber
    : identifier LPAREN Number_ RPAREN
    ;

// OCTET STRING and OBJECT IDENTIFIER are built-in ASN.1 type names written
// as two tokens; bare BITS (no inline enumeration) shows up as a SEQUENCE
// field placeholder type in real MIBs (e.g. EtherLike-MIB Dot3ControlEntry)
// when the actual enumeration is only given on the OBJECT-TYPE that uses
// it; everything else is a single identifier reference.
typeReference
    : identifier
    | OCTET STRING_TYPE
    | OBJECT IDENTIFIER
    | BITS
    ;

constraintPart
    : LPAREN constraint RPAREN
    ;

constraint
    : SIZE LPAREN rangeList RPAREN
    | rangeList
    ;

rangeList
    : rangeItem (PIPE rangeItem)*
    ;

rangeItem
    : boundValue (DOTDOT boundValue)?
    ;

boundValue
    : MINUS? Number_
    | HexString_
    | BinString_
    | identifier
    ;

// --- OBJECT IDENTIFIER values -------------------------------------------------

objectIdentifierValue
    : LBRACE oidComponentList RBRACE
    ;

oidComponentList
    : oidComponent+
    ;

oidComponent
    : identifier LPAREN Number_ RPAREN
    | identifier
    | Number_
    ;

// --- generic value -------------------------------------------------------------

value
    : string_
    | Number_
    | MINUS Number_
    | identifier
    | HexString_
    | BinString_
    ;

string_
    : String_
    ;

identifier
    : Identifier_UpperStart
    | Identifier_LowerStart
    | Identifier_DigitStart
    | keywordAsIdentifier
    ;

// A handful of SMI keywords are legal as plain identifiers in some dialects
// (e.g. vendor MIBs using "index" or "status" as a field name). Kept narrow
// on purpose; extend only with evidence from a failing real-world MIB.
keywordAsIdentifier
    : GROUP
    | OBJECT
    ;

// ---------------------------------------------------------------------
// Lexer rules
// ---------------------------------------------------------------------

// Keywords (must precede the generic identifier rules)
MODULE_IDENTITY        : 'MODULE-IDENTITY';
OBJECT_IDENTITY         : 'OBJECT-IDENTITY';
OBJECT_TYPE              : 'OBJECT-TYPE';
NOTIFICATION_TYPE        : 'NOTIFICATION-TYPE';
TRAP_TYPE                : 'TRAP-TYPE';
TEXTUAL_CONVENTION       : 'TEXTUAL-CONVENTION';
OBJECT_GROUP             : 'OBJECT-GROUP';
NOTIFICATION_GROUP       : 'NOTIFICATION-GROUP';
MODULE_COMPLIANCE        : 'MODULE-COMPLIANCE';
AGENT_CAPABILITIES       : 'AGENT-CAPABILITIES';
MACRO                    : 'MACRO';

LAST_UPDATED             : 'LAST-UPDATED';
ORGANIZATION             : 'ORGANIZATION';
CONTACT_INFO             : 'CONTACT-INFO';
DESCRIPTION              : 'DESCRIPTION';
REVISION                 : 'REVISION';
REFERENCE                : 'REFERENCE';
STATUS                   : 'STATUS';
SYNTAX                   : 'SYNTAX';
UNITS                    : 'UNITS';
MAX_ACCESS               : 'MAX-ACCESS';
ACCESS                   : 'ACCESS';
MIN_ACCESS               : 'MIN-ACCESS';
INDEX                    : 'INDEX';
AUGMENTS                 : 'AUGMENTS';
IMPLIED                  : 'IMPLIED';
DEFVAL                   : 'DEFVAL';
OBJECTS                  : 'OBJECTS';
NOTIFICATIONS            : 'NOTIFICATIONS';
ENTERPRISE               : 'ENTERPRISE';
VARIABLES                : 'VARIABLES';
DISPLAY_HINT             : 'DISPLAY-HINT';
MODULE                   : 'MODULE';
MANDATORY_GROUPS         : 'MANDATORY-GROUPS';
GROUP                    : 'GROUP';
OBJECT                   : 'OBJECT';
WRITE_SYNTAX             : 'WRITE-SYNTAX';
PRODUCT_RELEASE          : 'PRODUCT-RELEASE';
SUPPORTS                 : 'SUPPORTS';
INCLUDES                 : 'INCLUDES';
VARIATION                : 'VARIATION';
CREATION_REQUIRES        : 'CREATION-REQUIRES';

NOT_ACCESSIBLE           : 'not-accessible';
ACCESSIBLE_FOR_NOTIFY    : 'accessible-for-notify';
READ_ONLY                : 'read-only';
READ_WRITE               : 'read-write';
READ_CREATE              : 'read-create';
WRITE_ONLY               : 'write-only';

CURRENT_                 : 'current';
DEPRECATED               : 'deprecated';
OBSOLETE                 : 'obsolete';
MANDATORY                : 'mandatory';
OPTIONAL_                : 'optional';

DEFINITIONS              : 'DEFINITIONS';
BEGIN_                   : 'BEGIN';
END_                     : 'END';
EXPORTS                  : 'EXPORTS';
IMPORTS                  : 'IMPORTS';
FROM                     : 'FROM';
SEQUENCE                 : 'SEQUENCE';
OF                       : 'OF';
BITS                     : 'BITS';
SIZE                     : 'SIZE';
IDENTIFIER               : 'IDENTIFIER';
OCTET                    : 'OCTET';
STRING_TYPE              : 'STRING';

ASSIGN_OP                : '::=';
LBRACE                   : '{';
RBRACE                   : '}';
LPAREN                   : '(';
RPAREN                   : ')';
COMMA                    : ',';
SEMI                      : ';';
PIPE                      : '|';
DOTDOT                    : '..';
MINUS                     : '-';

// Strict ASN.1 identifiers are [A-Za-z][A-Za-z0-9-]*, but real vendor MIBs
// (e.g. Moxa mxProductInfo.mib: "mdsG4012_L3") use underscores too - allowed
// here as a dialect leniency.
Identifier_UpperStart
    : [A-Z] [A-Za-z0-9_-]*
    ;

Identifier_LowerStart
    : [a-z] [A-Za-z0-9_-]*
    ;

// Named-number labels starting with a digit (e.g. "1st(1)", "802dot3(10006)")
// appear in real MIBs even though strict ASN.1 identifiers can't start with
// a digit (SPEC.md SS11). Longest-match means a bare "123" still lexes as
// Number_ since this rule requires at least one trailing letter.
Identifier_DigitStart
    : [0-9]+ [A-Za-z] [A-Za-z0-9_-]*
    ;

Number_
    : [0-9]+
    ;

HexString_
    : '\'' [0-9A-Fa-f]* '\'' [Hh]
    ;

BinString_
    : '\'' [01]* '\'' [Bb]
    ;

String_
    : '"' (~["] | '""')* '"'
    ;

LineComment
    : '--' ~[\r\n]* -> skip
    ;

Whitespace
    : [ \t\r\n]+ -> skip
    ;
